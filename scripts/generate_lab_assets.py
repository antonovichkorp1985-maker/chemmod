#!/usr/bin/env python3
"""Original ChemMod pixel textures and cuboid models; stdlib only, reproducible.
Palette bands describe vial content *type*, not liquid colour, quantity or phase.
"""
import json
from pathlib import Path
import struct
import zlib
ROOT = Path(__file__).resolve().parents[1] / 'mod/src/main/resources'
ASSETS = ROOT / 'assets/chemmod'

def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n')

def texture(name, base, accent, style='metal'):
    w = 32
    pixels = [[base for _ in range(w)] for _ in range(w)]
    def rect(x0,y0,x1,y1,c):
        for y in range(y0,y1+1):
            for x in range(x0,x1+1): pixels[y][x] = c
    dark = (44,59,63,255)
    light = (231,242,236,255)
    rect(0,0,31,0,light); rect(0,0,0,31,light)
    rect(0,31,31,31,dark); rect(31,0,31,31,dark)
    if style == 'wood':
        for y in (7,15,23):
            rect(1,y,30,y,accent); rect(5,y+2,18,y+2,accent)
    elif style == 'glass':
        rect(3,2,4,29,light); rect(7,2,7,24,light)
        for y in (5,10,15,20): rect(22,y,29,y,dark)
    elif style in ('label','mixture'):
        rect(2,2,29,29,light); rect(3,4,28,10,accent)
        for y in (15,20,25): rect(6,y,23,y,dark)
        if style == 'mixture':
            for x in range(3,28,6):rect(x,4,x+2,10,dark)
    elif style.startswith('panel'):
        rect(4,5,27,23,dark); rect(6,7,25,16,(132,194,195,255))
        rect(7,9,8,14,light);rect(12,9,13,14,light);rect(17,9,18,14,light)
        rect(7,20,10,22,accent);rect(14,20,24,22,light)
    else:
        for y in (6,14,22):rect(3,y,28,y,accent)
        for x,y in ((3,3),(27,3),(3,27),(27,27)):rect(x,y,x+1,y+1,dark)
    raw = b''.join(b'\x00'+bytes(c for p in row for c in p) for row in pixels)
    def chunk(t,d):return struct.pack('!I',len(d))+t+d+struct.pack('!I',zlib.crc32(t+d)&0xffffffff)
    png=b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('!2I5B',w,w,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw))+chunk(b'IEND',b'')
    path=ASSETS/f'textures/block/lab/{name}.png';path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(png)

for args in [
    ('wood',(171,119,69,255),(127,80,46,255),'wood'),
    ('steel',(165,183,187,255),(144,164,170,255),'metal'),
    ('dark',(63,81,87,255),(83,100,105,255),'metal'),
    ('glass',(171,219,221,255),(119,177,184,255),'glass'),
    ('empty_label',(198,203,197,255),(143,151,148,255),'label'),
    ('pure_label',(198,203,197,255),(37,139,150,255),'label'),
    ('mixture_label',(198,203,197,255),(211,152,55,255),'mixture'),
    ('panel',(165,183,187,255),(70,87,93,255),'panel'),
    ('panel_active',(165,183,187,255),(238,134,46,255),'panel_active'),
]: texture(*args)

FACES=('north','south','east','west','up','down')
def box(a,b,texture):
    return {'from':a,'to':b,'faces':{f:{'texture':'#'+texture,'uv':[0,0,16,16]} for f in FACES}}
DISPLAY={'gui':{'rotation':[25,225,0],'translation':[0,0,0],'scale':[0.85,0.85,0.85]},
         'ground':{'translation':[0,3,0],'scale':[0.5,0.5,0.5]},
         'fixed':{'rotation':[0,0,0],'translation':[0,0,0],'scale':[1,1,1]},
         'firstperson_righthand':{'rotation':[0,45,0],'translation':[0,1,0],'scale':[0.65,0.65,0.65]},
         'thirdperson_righthand':{'rotation':[75,45,0],'translation':[0,2.5,0],'scale':[0.5,0.5,0.5]}}
def model(name,elements,textures,folder='block',display=None):
    write(ASSETS/f'models/{folder}/{name}.json',{'textures':{**{k:'chemmod:block/lab/'+v for k,v in textures.items()},'particle':'chemmod:block/lab/'+next(iter(textures.values()))},'elements':elements,'display':display or DISPLAY})
rack=[box([1,0,1],[7,1,15],'wood'),box([1,1,1],[2,8,15],'wood'),box([6,1,1],[7,8,15],'wood')]
# Crossbars make six individual visible openings along the upper shelf.
for z in [1,3.6,5.8,8,10.2,12.4,14.6]:rack.append(box([2,6,z],[6,7,min(15,z+0.4)],'wood'))
model('test_tube_rack',rack,{'wood':'wood'})
tray=[box([9,0,1],[15,0.6,15],'steel'),box([9,0.6,1],[9.5,2,15],'steel'),box([14.5,0.6,1],[15,2,15],'steel'),box([9,0.6,1],[15,2,1.5],'steel'),box([9,0.6,14.5],[15,2,15],'steel')]
model('laboratory_tray',tray,{'steel':'steel'})
write(ASSETS/'blockstates/laboratory_holder.json',{'multipart':[
    {'when':{part:'true', 'facing': facing},
     'apply':{'model':'chemmod:block/'+model_name, 'y': angle}}
    for facing, angle in [('north',0),('east',90),('south',180),('west',270)]
    for part, model_name in [('rack','test_tube_rack'),('tray','laboratory_tray')]
]})
for name,shift in [('test_tube_rack',4),('laboratory_tray',-4)]:
    display={**DISPLAY,'gui':{'rotation':[25,225,0],'translation':[shift,0,0],'scale':[1,1,1]}}
    write(ASSETS/f'models/item/{name}.json',{'parent':'chemmod:block/'+name,'display':display})
for variant,label in [('', 'empty_label'),('_pure','pure_label'),('_mixture','mixture_label')]:
    elements=[box([6,1,6],[10,11,10],'glass'),box([6.5,11,6.5],[9.5,12,9.5],'glass'),box([6,12,6],[10,13,10],'cap'),box([5.95,3,5.95],[10.05,7,10.05],'label')]
    model('substance_vial'+variant,elements,{'glass':'glass','cap':'dark','label':label},'item',
          {**DISPLAY,'gui':{'rotation':[0,0,0],'translation':[0,1,0],'scale':[1.2,1.2,1.2]}})
p=ASSETS/'models/item/substance_vial.json';m=json.loads(p.read_text());m['overrides']=[{'predicate':{'chemmod:contents':1},'model':'chemmod:item/substance_vial_pure'},{'predicate':{'chemmod:contents':2},'model':'chemmod:item/substance_vial_mixture'}];write(p,m)
for suffix in ('','_active'):
    elements=[box([0,0,0],[16,3,16],'dark'),box([2,3,2],[14,12,14],'steel'),box([3,12,3],[13,14,13],'steel'),box([6,14,6],[10,16,10],'dark'),box([3,4,1.8],[13,10,2],'panel')]
    model('chemical_reactor'+suffix,elements,{'steel':'steel','dark':'dark','panel':'panel'+suffix})
write(ROOT/'data/chemmod/loot_table/blocks/laboratory_holder.json',{'type':'minecraft:block','pools':[]})
for name,pattern,key in [
    ('test_tube_rack',['S S','PPP','S S'],{'S':{'item':'minecraft:stick'},'P':{'tag':'minecraft:planks'}}),
    ('laboratory_tray',['I I','III'],{'I':{'item':'minecraft:iron_ingot'}}),
]:write(ROOT/f'data/chemmod/recipe/{name}.json',{'type':'minecraft:crafting_shaped','category':'misc','pattern':pattern,'key':key,'result':{'id':'chemmod:'+name,'count':1}})
