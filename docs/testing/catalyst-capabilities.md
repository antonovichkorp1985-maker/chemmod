# ChemMod — катализ как выводимое свойство, а не список предметов

**Дата:** 10 октября 2026
**Тип среза:** свойство + условие правила. Список допустимых предметов-катализаторов не появился.

## 1. Что было

Реактор проверял катализатор только по тегам предмета: `ChemicalReactorBlockEntity.catalystTags()`
проходит по `CATALYST_TAG_IDS` (они выводятся из правил) и спрашивает `catalyst.is(TagKey…)`.
То есть «что считается никелевым катализатором» решал список предметов в датапаке, а не состав
вещества. Поэтому правило, требующее никель, было мёртвым, пока кто-то не заведёт предмет и тег.

## 2. Что стало

`core/src/main/kotlin/io/github/antonovichkorp/chemmod/core/catalysis/CatalystModel.kt` выводит
**способности** из состава:

- `CatalystCapabilityRule(id, requiredElements, anyElements)` — способность зарабатывается набором
  элементов, а не именем предмета;
- `CatalystModel.earnedBy(elements)` — состав → способности;
- `elementsOfMaterial(materialId)` — элементы объёмного материала: минерал даёт стехиометрию,
  species даёт элементы графа, композит обходится рекурсивно;
- `capabilitiesOfMaterial(id)` и `capabilitiesOfStructure(structure)` — точки входа для Java.

Содержимое `core/src/main/resources/chemmod/catalysis/default.json`:

| Способность | Зарабатывается элементами |
| --- | --- |
| `chemmod:hydrogenation_metal` | Ni, Pd, Pt, Rh, Ru, Cu, Fe |
| `chemmod:dehydrogenation_metal` | Cu, Cr, Zn, Fe |
| `chemmod:oxidation_metal` | V, Mo, W, Mn, Cr |

`ReactionConditions.catalystCapabilities` — новое условие правила; `ReactionEnvironment`
получил `catalystCapabilities` (конструктор помечен `@JvmOverloads`, прежние вызовы не сломаны),
и `ReactionEngine.conditionsMet` требует `containsAll`. Реактор считает способности предмета в
катализаторном слоте: `MaterialFormItem.materialId()` → `CatalystModel.capabilitiesOfMaterial(…)`.

Медь зарабатывает `hydrogenation_metal` и `dehydrogenation_metal` из своей стехиометрии `Cu`,
поэтому новые правила доступны **с уже существующим медным слитком**, без новых предметов.

## 3. Новые правила (16 всего)

| Правило | Превращение | Требуемая способность |
| --- | --- | --- |
| `chemmod:nitrile_hydrogenation` | C≡N → C=N + H₂ | `chemmod:hydrogenation_metal` |
| `chemmod:imine_hydrogenation` | C=N → C–N + H₂ | `chemmod:hydrogenation_metal` |
| `chemmod:amine_dehydrogenation` | C–N → C=N, выделяя H₂ | `chemmod:dehydrogenation_metal` |

Цепочка замкнута: нитрил → имин → амин и обратно. Проверено: `CC#N` + H₂ → `C2H5N`,
`CC=N` + H₂ → `C2H7N`, `CCN` → `C2H5N` + `H2`; все исходы проходят `isConserved()`.

Старый механизм тегов сохранён: четыре существующих правила по-прежнему требуют
`chemmod:palladium`, `chemmod:chromium_oxide`, `chemmod:nickel`, `chemmod:copper`, и их поведение
не изменилось.

## 4. Чего срез не делает

- Кислотный катализ пробиркой (серная кислота как донор `acid_site`) не подключён: слот катализатора
  читает только `MaterialFormItem`, для колбы нужен отдельный путь.
- Способности не показываются в интерфейсе и не объясняются в тултипе.
- Pd, Ni, Cr как материалы не добавлены: минеральные источники в каталоге уже есть
  (`chemmod:millerite` NiS, `chemmod:pentlandite` Fe4Ni5S8, `chemmod:chromite` FeCr2O4), но
  материалы `chemmod:nickel` / `chemmod:chromium` и их `supportedForms` не заведены, а предметы
  без моделей и текстур выпускать нельзя.

## 5. Проверка

- `core/src/test/kotlin/.../core/catalysis/CatalystModelTest.kt` — 6 тестов: медь зарабатывает две
  способности, силикат не зарабатывает ничего, способности следуют за контентом материала,
  неизвестный материал не роняет вывод, молекула судится по своему графу.
- `core/src/test/kotlin/.../core/reaction/CatalystCapabilityRuleTest.kt` — 5 тестов: нитрил → имин,
  имин → амин, амин → имин, чужая способность не работает, температурный гейт и требование H₂.
- `mod/src/main/java/.../gametest/ChemicalReactorCatalystGameTests.java` — 2 геймтеста: медный
  слиток в катализаторном слоте проводит гидрирование нитрила (`CC#N` + `H₂` → `CC=N`, катализатор
  не расходуется, правило `chemmod:nitrile_hydrogenation`), а без металла реактор не реагирует и
  показывает `reactor_status.chemmod.needs_catalyst`.
- Исполняемая проверка — CI (локально JDK нет); результат в JUnit-аннотации прогона.
