"""Compare garment/leg triangle overlaps and face-area ratios in sampled poses.

Run with Blender: --python tools/draconia_audit_garment_follow.py -- BEFORE.blend AFTER.blend
Counts are diagnostics, not proof of collision-free deformation.
"""
import bpy,math,json,sys
from mathutils import Vector
from mathutils.bvhtree import BVHTree
poses=[('rest',{})]
for side in ('L','R'):
 for axis in (0,2):
  for angle in (-60,-45,-30,-15,15,30,45,60):poses.append((f'{side}_{axis}_{angle}',{f'Thigh.{side}':tuple(angle if i==axis else 0 for i in range(3))}))
for a in (-40,40):
 for b in (-40,40):poses.append((f'both_{a}_{b}',{'Thigh.L':(a,0,0),'Thigh.R':(b,0,0),'Torso':(0,0,15)}))
def mesh(o):
 d=bpy.context.evaluated_depsgraph_get();ob=o.evaluated_get(d);m=ob.to_mesh();v=[ob.matrix_world@x.co for x in m.vertices];p=[x.vertices[:] for x in m.polygons];ob.to_mesh_clear();return v,p
def tree(v,p):return BVHTree.FromPolygons(v,p,all_triangles=False,epsilon=1e-6)
def area(p,v):return sum((v[p[i]]-v[p[0]]).cross(v[p[i+1]]-v[p[0]]).length*.5 for i in range(1,len(p)-1))
results=[]
for path in sys.argv[sys.argv.index('--')+1:]:
 bpy.ops.wm.open_mainfile(filepath=path);rig=bpy.data.objects['Draconia Body Rig - experimental'];res={};base={}
 for label,pose in poses:
  for b in rig.pose.bones:b.rotation_mode='XYZ';b.rotation_euler=(0,0,0)
  for bn,angles in pose.items():rig.pose.bones[bn].rotation_euler=[math.radians(a) for a in angles]
  bpy.context.view_layer.update();meshes={n:mesh(bpy.data.objects[n]) for n in ('Cloak','Skirt','Legs')};leg=tree(*meshes['Legs']);row={}
  for n in ('Cloak','Skirt'):
   v,p=meshes[n];row[n+'_pairs']=len(tree(v,p).overlap(leg));a=[area(poly,v) for poly in p]
   if label=='rest':base[n]=a
   ratios=[x/y for x,y in zip(a,base[n]) if y>1e-8];ratios.sort();row[n+'_p99']=ratios[int(.99*(len(ratios)-1))];row[n+'_max']=max(ratios)
  res[label]=row
 results.append(res)
for n in ['Cloak','Skirt']:
 for idx,res in enumerate(results):print('SUMMARY',n,idx,'collisions_total',sum(v[n+'_pairs'] for v in res.values()),'colliding_poses',sum(v[n+'_pairs']>0 for v in res.values()),'max_area',round(max(v[n+'_max'] for v in res.values()),3),flush=True)
for name in results[0]:
 a=results[0][name];b=results[1][name]
 if a['Cloak_pairs']!=b['Cloak_pairs'] or a['Skirt_pairs']!=b['Skirt_pairs']:print(name,'pairs before/after',a['Cloak_pairs'],b['Cloak_pairs'],a['Skirt_pairs'],b['Skirt_pairs'],flush=True)
