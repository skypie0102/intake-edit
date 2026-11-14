"""Compare UV-corresponding neutral sleeve geometry in two Blender scenes.

blender -b --python-exit-code 1 --python tools/draconia_audit_sleeve_symmetry.py -- BEFORE.blend AFTER.blend
"""
import bpy
import sys
from collections import defaultdict
from mathutils import Vector
paths=sys.argv[sys.argv.index('--')+1:]
if len(paths)!=2:raise ValueError('Expected BEFORE.blend AFTER.blend')
records=[];maps={}
for n,path in enumerate(paths):
 bpy.ops.wm.open_mainfile(filepath=path);d={}
 for name in ('Cloak','Jacket Hands'):
  o=bpy.data.objects[name];m=o.data;points=[o.matrix_world@v.co for v in m.vertices];d[name]=points
  if n:continue
  uv=m.uv_layers.active.data;per=defaultdict(set)
  for f in m.polygons:
   for i in f.loop_indices:
    p=uv[i].uv;per[m.loops[i].vertex_index].add((round(p.x,5),round(p.y,5)))
  groups={s:{g.index for g in o.vertex_groups if g.name in ('UpperArm.'+s,'Forearm.'+s)} for s in ('L','R')};left=[];right=[]
  for v in m.vertices:
   p=points[v.index]
   if name=='Jacket Hands':
    if p.x<0:left.append(v)
    elif p.x>0:right.append(v)
   elif p.x<-.12 and sum(g.weight for g in v.groups if g.group in groups['L'])>.15:left.append(v)
   elif p.x>.12 and sum(g.weight for g in v.groups if g.group in groups['R'])>.15:right.append(v)
  lookup=defaultdict(list)
  for v in right:
   for k in per[v.index]:lookup[k].append(v.index)
  pairs=[]
  for v in left:
   c={i for k in per[v.index] for i in lookup[k]}
   if not c:continue
   p=points[v.index];q=Vector((-p.x,p.y,p.z));i=min(c,key=lambda j:(points[j]-q).length);pairs.append((v.index,i))
  maps[name]=pairs
 records.append(d)
for name,pairs in maps.items():
 for path,d in zip(paths,records):
  points=d[name];ds=[]
  for a,b in pairs:
   p=points[a];q=points[b].copy();q.x=-q.x;ds.append((p-q).length)
  ds.sort();print('MATCH',path,name,'VERTS',len(ds),'MEAN',sum(ds)/len(ds),'MEDIAN',ds[len(ds)//2],'P90',ds[int(.9*len(ds))],'MAX',max(ds),flush=True)
