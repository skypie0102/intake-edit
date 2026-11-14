"""Check sleeve faces against UV-corresponding opposite-side faces.

blender -b --python-exit-code 1 --python tools/draconia_validate_sleeve_correspondence.py -- BEFORE.blend AFTER.blend
"""
import bpy
import sys
from mathutils import Vector
from collections import defaultdict,Counter
paths=sys.argv[sys.argv.index('--')+1:]
if len(paths)!=2:raise ValueError('Expected BEFORE.blend AFTER.blend')
for path in paths:
 bpy.ops.wm.open_mainfile(filepath=path)
 for name in ('Cloak','Jacket Hands'):
  o=bpy.data.objects[name];m=o.data;uv=m.uv_layers.active.data;per=defaultdict(set)
  for f in m.polygons:
   for i in f.loop_indices:
    p=uv[i].uv;per[m.loops[i].vertex_index].add((round(p.x,5),round(p.y,5)))
  groups={s:{g.index for g in o.vertex_groups if g.name in ('UpperArm.'+s,'Forearm.'+s)} for s in ('L','R')};ls=[];rs=[]
  for v in m.vertices:
   p=o.matrix_world@v.co
   if name=='Jacket Hands':
    if p.x<0:ls.append(v)
    elif p.x>0:rs.append(v)
   else:
    if p.x<-.12 and sum(g.weight for g in v.groups if g.group in groups['L'])>.15:ls.append(v)
    elif p.x>.12 and sum(g.weight for g in v.groups if g.group in groups['R'])>.15:rs.append(v)
  lookup=defaultdict(list)
  for v in rs:
   for k in per[v.index]:lookup[k].append(v.index)
  pos=[o.matrix_world@v.co for v in m.vertices];mapping={}
  for v in ls:
   candidates={i for k in per[v.index] for i in lookup[k]}
   if candidates:
    p=pos[v.index];q=Vector((-p.x,p.y,p.z));mapping[v.index]=min(candidates,key=lambda i:(pos[i]-q).length)
  faces={frozenset(f.vertices):f for f in m.polygons};stats=[]
  for f in m.polygons:
   if all(i in mapping for i in f.vertices):
    target=faces.get(frozenset(mapping[i] for i in f.vertices))
    if target is None:continue
    def normal(face,mirror=False):
     vs=[pos[i].copy() for i in face.vertices]
     if mirror:
      for p in vs:p.x=-p.x
     n=sum(((vs[i]-vs[0]).cross(vs[i+1]-vs[0]) for i in range(1,len(vs)-1)),Vector())
     if mirror:n.negate()
     return n.length,n.normalized()
    a,na=normal(f);b,nb=normal(target,True)
    if b>1e-8:stats.append((a/b,na.dot(nb),f.index,target.index))
  print('REFLECT_CHECK',path,name,'FACES',len(stats),'NEG',sum(s[1]<0 for s in stats),'MIN_DOT',min((s[1] for s in stats),default=0),'MIN_RATIO',min((s[0] for s in stats),default=0),'MAX_RATIO',max((s[0] for s in stats),default=0),'BAD',[x for x in stats if x[1]<0][:5],flush=True)

  if path==paths[-1]:
   assert len(stats)>=(300 if name=='Cloak' else 60), (name,len(stats))
   assert all(item[1]>0 for item in stats), name
print('SLEEVE_CORRESPONDENCE_CHECK_OK',flush=True)
