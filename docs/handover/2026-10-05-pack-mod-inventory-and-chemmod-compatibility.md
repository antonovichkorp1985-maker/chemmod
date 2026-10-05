# Реальная сборка — полный инвентарь модов и контекст совместимости ChemMod

**Снимок проверен:** 5 октября 2026 по фактической папке `mods` экземпляра в Drive, обновлённой 4 октября 2026.
**Назначение:** обязательный контекст для следующих решений в ChemMod. Это не задание ChemMod перебалансировать или переписать всю сборку.

## Метод и границы точности

Проверены все 209 записей в фактической папке `mods`, а также ключевые конфиги Create, TFC, Almost Unified и живой KubeJS-каталог. Из 209 записей активными являются **205 JAR**. Остальные четыре записи перечислены в разделе «Неактивное/служебное» и не должны ошибочно считаться установленными модами.

Ниже приведён полный список активных JAR с версиями из фактических имён файлов. Это инвентаризация состава и его архитектурных пересечений, а не утверждение, что ChemMod должен интегрироваться с каждым модом.

## 1. Техническое ядро: что формирует прогрессию

### Среда, геология и материалы

- **TerraFirmaCraft 4.2.11** — среда, геология, климат и типы воды; ChemMod обязан корректно распознавать TFC-воду, включая солёную.
- **Tectonic 3.0.28**, **Alex's Caves 1.1.1**, **YUNG's Better Dungeons / Mineshafts / Strongholds / Temples / Fortresses / Monuments / Witch Huts / End Island** — генерация мира и структуры; не создавать предположений о vanilla-геологии как единственном источнике руд.
- **Big Water 1.2.0** и **PMWeather 0.17.14-alpha** — дополнительные факторы водной среды/погоды. Они не являются уже реализованными адаптерами ChemMod: новый водный источник должен быть исследован отдельно, а не объявлен автоматически TFC-водой.
- **Almost Unified 1.4.2** — критическая точка для общих `c:`-тегов металлов и форм.

### Промышленность и энергия

Одновременно присутствуют несколько самостоятельных технических лестниц:

- **Create 6.0.10** и крупная экосистема аддонов;
- **GTCEu 7.0.2** плюс локальный `gtbridge-1.0.0`;
- **Mekanism 10.7.19.85**, Generators и Tools;
- **Immersive Engineering 12.4.2**;
- **Ender IO 8.2.12-beta**;
- **PneumaticCraft: Repressurized 8.2.23**;
- **HBM's NTM 198A**;
- **TFMG 1.2.0**, Create Diesel Generators, Create New Age, CreateAddition и **Power Grid 0.6.0.1**.

Это означает: ChemMod не может считать себя единственным источником меди во всей сборке только по факту собственного кода. Его роль — иметь собственные канонические физические партии и процессы; решение о глобальной унификации, отключении чужих цепочек или квестовой очередности остаётся за владельцем сборки.

### Ресурсное производство, логистика и автоматизация

- **Applied Energistics 2 19.2.17**, AE Additions, ExtendedAE, AE2WTLib, Rechiseled AE2;
- **Ex Nihilo Sequentia 7.0.3.5** и Ex Compressum;
- **Create Ore Excavation 1.6.8**, Additional Enchanted Miner, Productive Bees;
- **Iron Chests**, Sophisticated Backpacks, Create Ender Transmission, Create Connected, Create Railways/Modern Train Parts/Tracks Plus;
- **Polymorph** для выбора конфликтующих рецептов.

Будущие ChemMod-машины должны заранее рассчитывать на автоматический ввод/вывод и на существование альтернативных путей получения сырья. В то же время они не должны молча принимать чужой безкомпонентный предмет как физическую партию ChemMod.

## 2. Критические факты, влияющие на ChemMod

### 2.1 Create — реальная среда M5

В экземпляре установлен именно `create-1.21.1-6.0.10.jar`, соответствующий зависимости M5. В server-config:

- stress включён;
- `mediumSpeed = 30 RPM`;
- `fastSpeed = 100 RPM`;
- максимальная скорость — 256 RPM;
- базовый impact мельницы — 4, дробильных колёс и механического пресса — 8.

Поэтому кинетический сепаратор ChemMod с требованием `MEDIUM` и impact 4 привязан к реальным правилам этого экземпляра: 30 RPM и 120 SU на минимальной скорости. В паке есть не «просто Create», а плотная система из Create-аддонов; будущие решения должны проверяться именно рядом с ними.

### 2.2 TFC и вода

Установлен TFC 4.2.11, а в `tfc-common.toml` включён default world preset `tfc:overworld`. Это подтверждает, что TFC — фактическая среда ChemMod, а не гипотетическая необязательная интеграция. Совместимость колбы с TFC-пресной и солёной водой обязательна; для Big Water нужны отдельные тесты и явное решение.

### 2.3 Almost Unified — материалная граница

Фактический `almostunified/materials.json` объединяет формы:

```text
c:dusts, c:gears, c:gems, c:ingots, c:nuggets, c:ores,
c:plates, c:raw_materials, c:rods, c:storage_blocks, c:wires
```

Текущий порядок приоритета: `minecraft` → `kubejs` → `create` → `immersiveengineering` → `mekanism`. **ChemMod в приоритетах отсутствует.**

Следствие для разработки:

1. существующие рецепты ChemMod с прямыми идентификаторами ChemMod не должны самопроизвольно заменить собственную материальную партию чужой формой;
2. прежде чем переводить вход/выход ChemMod на общий `c:`-тег, нужно отдельно согласовать с владельцем сборки вопрос приоритета ChemMod и правила мостов;
3. ChemMod не меняет конфиг Almost Unified самовольно — это pack-wide решение.

### 2.4 KubeJS и локальные патчи

Активен **KubeJS 2101.7.1-build.181**. В доступном корневом каталоге KubeJS находятся только стандартные example-скрипты в `client_scripts`, `server_scripts` и `startup_scripts`; активной авторской рецептурной логики ChemMod там не найдено.

Но часть JAR явно отмечена `kjs181patch` (`create_mechanical_extruder`, `create_mechanical_spawner`, `createoreexcavation`, `mechanicals`, `wariumce`), а также есть локальный `gtbridge-1.0.0`. Поэтому отсутствие скриптов не означает стандартную среду: перед будущим compat-кодом нужно проверять реальные JAR и runtime-логи, а не обновлять KubeJS без согласования.

### 2.5 Квесты и отображение рецептов

- Установлены **FTB Quests 2101.1.36**, FTB Library, FTB Teams и FTB Backups.
- В доступной ветке мира есть состояние игрока без выполненных задач; определения глав FTB Quests в этой экспортированной ветке не найдены.
- Установлен **EMI 1.1.24** с EMI Ores, EMI Enchanting и EMI Professions; JEI среди активных JAR нет.

ChemMod обязан делать рецепты и подсказки понятными для EMI, но создание/балансирование FTB-квестов остаётся за владельцем сборки и другим агентом.

### 2.6 Устаревший JAR ChemMod

В текущей папке `mods` всё ещё лежит `chemmod-0.0.2-test.1.jar`. Перед проверкой `v0.8.0-test.1` его нужно удалить, иначе два JAR с одним `modId` создадут конфликт/неопределённую загрузку.

## 3. Полный инвентарь активных JAR

### A–C

```text
accdelight-1.0.0-neoforge+1.21.1.jar
Ad-Astra-Giselle-Addon-neoforge-1.21.1-8.1.jar
adastra-1.21.1-1.16.26-neoforge.jar
AdditionalEnchantedMiner-1.21.1-neoforge-21.1.164.jar
ae2wtlib-19.5.1.jar
AEAdditions-1.21.1-6.0.2.jar
aeronauticsplus-0.2.2-test.8.jar
aeronauticswinds-1.3.1.jar
AgriCraft-neoforge-1.21.1-4.0.17.jar
alexscaves-1.1.1-neoforge+1.21.1.jar
alexsmobs-2.2.2-neoforge+1.21.1.jar
almostunified-neoforge-1.21.1-1.4.2.jar
alternate_current-mc1.21-1.9.0.jar
amcdelight-1.0.1-neoforge+1.21.1.jar
appleskin-neoforge-mc1.21-3.0.9.jar
appliedenergistics2-19.2.17.jar
architectury-13.0.11-neoforge.jar
athena-neoforge-1.21.1-4.0.6.jar
baguettelib-1.21.1-NeoForge-2.0.7.jar
balm-neoforge-1.21.1-21.0.66.jar
bellsandwhistles-0.4.7-1.21.1.jar
better-advanced-tooltips-2101.1.0-build.5.jar
betterdays-1.21.1-3.3.6.3-NEOFORGE.jar
bigwater-1.2.0-neoforge+mc1.21.1.jar
burnt-1.10.5.1-neoforge-1.21.1.jar
cannoncompressedarmor-1.0.0.jar
carryon-neoforge-1.21.1-2.2.6.13.jar
cbc_going_ballistic-0.3.1.jar
CBC-Military-Supplement-1.21.1-2.1.4.jar
chemmod-0.0.2-test.1.jar
Chest-Preview-NeoForge-1.21.1-1.0.0.jar
chipped-neoforge-1.21.1-4.0.2.jar
chisel-1.21.1-NeoForge-1.4.1.jar
chisels-and-bits-neoforge-21.1.32.jar
Chunky-NeoForge-1.4.23.jar
citadel-1.21.1-2.7.6.jar
cloth-config-15.0.140-neoforge.jar
Clumps-neoforge-1.21.1-19.0.0.1.jar
codxlib-1.6.1-neoforge+1.21.1.jar
comforts-neoforge-9.0.5+1.21.1.jar
common-storage-lib-neoforge-1.21.1-0.0.10.jar
configuration-neoforge-1.21.1-3.1.1.jar
Controlling-neoforge-1.21.1-19.0.5.jar
copycats-3.0.9+mc.1.21.1-neoforge.jar
cosmeticarmorreworked-1.21.1-v1-neoforge.jar
create_aero_radar-0.1.1-1.21.1.jar
create_connected-1.3.3-mc1.21.1.jar
create_hypertube-0.6.0-NEOFORGE.jar
create_jetpack-forge-5.2.1.jar
create_mechanical_extruder-1.21.1-2.2.2-6.0.10-kjs181patch.jar
create_mechanical_spawner-1.21.1-1.3.2-6.0.10-kjs181patch.jar
create_no_touching-1.0.8Neo-Aeronautics.jar
create_optical-0.4.2.jar
create_radar_mobile_radars-1.0.0.jar
create_radar-0.4.9.4-1.21.1.jar
create_ratatouille-1.21.1-1.4.0.jar
create_sa_curios_jetpacks-neoforge-1.21.1-1.2.4.jar
create_submarine-3.3.0.jar
create_tsr-1.1.0.jar
create-1.21.1-6.0.10.jar
create-aeronautics-bundled-1.21.1-1.3.2.jar
create-autonavigation-0.2.0.jar
create-central-kitchen-2.6.2.jar
create-confectionery1.21.1_v1.1.3b.jar
create-new-age-1.2.0+neoforge-mc1.21.1.jar
create-stuff-additions1.21.1_v2.1.4b.jar
createaddition-1.7.1.jar
createbigcannons-5.11.7+mc.1.21.1.jar
createdeco-2.1.3.jar
createdieselgenerators-1.21.1-1.3.15.jar
CreateDragonsPlus-1.11.9.jar
createendertransmission-2.1.1-1.21.1.jar
createfirefightingadd-0.2.3-beta.jar
createmobfarming-1.1.0.jar
createoreexcavation-1.21-1.6.8-kjs181patch.jar
createpropulsion-1.1.5.jar
CreateRadiologistics-1.1.2.jar
CreateTailwind-1.0.0+1.21.1-neoforge.jar
createtree-3.2.3.jar
CTM-1.21-1.2.1+3.jar
curios-neoforge-9.5.1+1.21.1.jar
custommusic-1.21.1-1.0.1.jar
```

### D–F

```text
dynamic-fps-3.11.4+minecraft-1.21.0-neoforge.jar
emi_enchanting-0.1.2+1.21+neoforge.jar
emi_ores-1.3+1.21.1+neoforge.jar
emi-1.1.24+1.21.1+neoforge.jar
EMIProfessions-neoforge-1.21.1-1.0.3.jar
emotecraft-for-MC1.21.1-2.4.12-neoforge.jar
enderio-8.2.12-beta.jar
entityculling-neoforge-1.11.2-mc1.21.1.jar
excompressum-neoforge-1.21.1-21.1.15.0.jar
ExNihiloSequentia-1.21.1-7.0.3.5-build.LOCAL.jar
ExtendedAE-1.21-2.2.35-neoforge.jar
extradelight-2.6.6.jar
FaradayEarsMod-1.21.1-1.5.2.jar
FarmersDelight-1.21.1-1.3.4.jar
ferritecore-7.0.3-neoforge.jar
firstperson-neoforge-2.7.2-mc1.21.1.jar
freecam-neoforge-1.3.0+mc1.21.jar
ftb-backups-3-21.1.5.jar
ftb-library-neoforge-2101.1.36.jar
ftb-quests-neoforge-2101.1.36.jar
ftb-teams-neoforge-2101.1.11.jar
fusion-1.3.15b-neoforge-mc1.21.1.jar
```

### G–N

```text
gaboulibs-neoforge-1.9.jar
geckolib-neoforge-1.21.1-4.9.3.jar
Glodium-1.21-2.2-neoforge.jar
gravestone-neoforge-1.21.1-1.0.40.jar
gtbridge-1.0.0.jar
gtceu-1.21.1-7.0.2.jar
guideme-21.1.19.jar
hbmsntm-198A.jar
ImmediatelyFast-NeoForge-1.6.14+1.21.1.jar
immersive_aircraft-1.5.2+1.21.1-neoforge.jar
ImmersiveEngineering-1.21.1-12.4.2-194.jar
interiors-0.6.1 v3.jar
InventoryProfilesNext-neoforge-1.21.1-2.2.5.jar
iris-neoforge-1.8.14-beta.1+mc1.21.1.jar
ironchest-1.21-neoforge-16.0.7.jar
Jade-1.21.1-NeoForge-15.10.6.jar
KeybindAtlas-v1.4.0-mc1.21.1-neoforge.jar
kotlinforforge-5.12.0-all.jar
Ksyxis-1.4.5.jar
kubejs-neoforge-2101.7.1-build.181.jar
lambdynamiclights-4.8.11+1.21.1.jar
ldlib2-neoforge-1.21.1-2.2.41-all.jar
libIPN-neoforge-1.21.1-6.6.3.jar
lithostitched-1.8.0-neoforge-21.1.jar
locometal_armor-1.1.0.jar
mcw-doors-1.1.5-mc1.21.1neoforge.jar
mcw-furniture-3.4.1-mc1.21.1neoforge.jar
mcw-lights-1.1.5-mc1.21.1neoforge.jar
mcw-mcwfences-1.2.1-mc1.21.1neoforge.jar
mcw-roofs-2.3.2-mc1.21.1neoforge.jar
mechanicals-1.21.1-1.1.6-kjs181patch.jar
Mekanism-1.21.1-10.7.19.85.jar
MekanismGenerators-1.21.1-10.7.19.85.jar
MekanismTools-1.21.1-10.7.19.85.jar
modernfix-neoforge-5.27.24+mc1.21.1.jar
moderntrainparts-0.2.5-neoforge-mc1.21.1-cr6.0.10.jar
morepropulsion-1.4.0.jar
MouseTweaks-neoforge-mc1.21-2.26.1.jar
MyNethersDelight-1.21.1-1.10.4.1.jar
NaturesCompass-1.21.1-3.4.0-neoforge.jar
noisium-neoforge-2.7.0+mc1.21-1.21.1.jar
notenoughanimations-neoforge-1.12.6-mc1.21.1.jar
NovaCore-1.21.1-4.0.1-build.LOCAL.jar
```

### O–Z

```text
ok_zoomer-neo-10.0.0-beta.13.jar
pamhc2crops-NEOFORGE-1.21.1-1.0.9.jar
pamhc2foodcore-NEOFORGE-1.21.1-1.0.4.jar
pamhc2trees-NEOFORGE-1.21.1-1.0.9.jar
Patchouli-1.21.1-93-NEOFORGE.jar
perspatium-1.21.1-1.1.0.jar
pipeorgans-0.8.2+1.21.1.jar
pmweather-1.21.1-0.17.14-alpha.jar
pneumaticcraft-repressurized-8.2.23+mc1.21.1.jar
polymorph-neoforge-1.2.0+1.21.1.jar
potentials-neoforge-1.21-0.7.1.jar
powergrid-mc1.21.1-0.6.0.1.jar
productivebees-1.21.1-13.13.5.jar
railways-0.3.0-beta.2+neoforge-mc1.21.1.jar
rechiseled_chipped-2.0-1.21.1.jar
rechiseled-1.2.6-neoforge-mc1.21.jar
rechiseledae2-neoforge-1.21-1.21.1-1.1.0.jar
resourcefulconfig-neoforge-1.21-3.0.11.jar
resourcefullib-neoforge-1.21-3.0.12.jar
rhino-2101.2.8-build.91.jar
ritchiesprojectilelib-2.1.2+mc.1.21.1-neoforge.jar
sable-dynamic-lights-1.21.1-2.0.1.jar
sable-neoforge-1.21.1-2.0.5.jar
ScalableCatsForce-NeoForge-3.7.1-build-11-with-library.jar
Searchables-neoforge-1.21.1-1.0.2.jar
sliceanddice-4.3.4-neoforge.jar
sodium-neoforge-0.8.13+mc1.21.1.jar
sophisticatedbackpacks-1.21.1-3.26.3.2158.jar
sophisticatedcore-1.21.1-1.5.1.2341.jar
sound-physics-remastered-neoforge-1.21.1-1.5.1.jar
spark-1.10.124-neoforge.jar
supermartijn642configlib-1.1.8-neoforge-mc1.21.jar
supermartijn642corelib-1.1.24a-neoforge-mc1.21.jar
tab_organizer-2.4.0-neoforge.jar
tacz-neoforge-1.21.1-1.1.8-hotfix-r6.jar
takesapillage-neoforge-1.0.12+mc1.21.1.jar
tectonic-3.0.28-neoforge-21.1.jar
TerraFirmaCraft-NeoForge-1.21.1-4.2.11.jar
terrain_slabs-neoforge-3.1.2.jar
tfmg-1.2.0.jar
tracks_plus-1.0.6b7.jar
treephysics-neoforge-1.21.1-2.4.jar
vortylib-1.2.5.2.jar
wariumce-1.0.3-kjs181patch.jar
weaversparadise-1.6.3.jar
xaerominimap-neoforge-1.21.1-26.5.0.jar
xaeroworldmap-neoforge-1.21.1-1.46.0.jar
YungsApi-1.21.1-NeoForge-5.1.9.jar
YungsBetterDesertTemples-1.21.1-NeoForge-4.1.5.jar
YungsBetterDungeons-1.21.1-NeoForge-5.1.4.jar
YungsBetterEndIsland-1.21.1-NeoForge-3.1.2.jar
YungsBetterJungleTemples-1.21.1-NeoForge-3.1.2.jar
YungsBetterMineshafts-1.21.1-NeoForge-5.1.1.jar
YungsBetterNetherFortresses-1.21.1-NeoForge-3.1.5.jar
YungsBetterOceanMonuments-1.21.1-NeoForge-4.1.2.jar
YungsBetterStrongholds-1.21.1-NeoForge-5.1.3.jar
YungsBetterWitchHuts-1.21.1-NeoForge-4.1.1.jar
ZeroCore2-1.21.1-2.4.21.jar
```

## 4. Неактивное/служебное — не считать модами

```text
.index/                                      # служебная папка индексатора
fzzy_config-0.7.7+1.21+neoforge.jar.ZBYHVE  # временный/переименованный не-JAR
emi_loot-0.7.9+1.21+neoforge.jar.SUKPyU      # временный/переименованный не-JAR
nuclearcraftneohaul-1.21.1-0.10.10-kjs181patch.jar.disabled
```

В частности, **NuclearCraft Neohaul сейчас отключён**. Его нельзя описывать как активную зависимость ChemMod, пока файл не будет возвращён в активный `.jar` и не пройдёт отдельная проверка.

## 5. Рабочие правила ChemMod после аудита

1. Всегда проверять изменения на фактических версиях Create 6.0.10 и TFC 4.2.11, а не на случайных аналогах.
2. Сохранять ChemMod самостоятельным: опциональные адаптеры не должны приводить классы сторонних модов в always-loaded путь.
3. Не делать глобальную унификацию/отключение чужих руд, слитков, рецептов, машин или worldgen через ChemMod.
4. Для новых общих item-тегов сначала оценивать Almost Unified и реальную приоритетную политику; текущая конфигурация не даёт ChemMod канонический приоритет.
5. Не считать отключённый NuclearCraft частью работающего техдерева.
6. Проверять отображение рецептов в EMI; не ожидать JEI.
7. Держать старый ChemMod JAR вне `mods` при тестировании нового релиза.
8. Перед реализацией любой новой интеграции читать соответствующие JAR/config/скрипты именно этой сборки и фиксировать результат в документации.
