"""Sample 180 seeded mixed poses; report BVH triangle-pair counts before/after.

Run: blender -b --python-exit-code 1 --python tools/draconia_audit_mixed_poses.py -- BEFORE.blend AFTER.blend
The overlaps are a limited diagnostic, not a complete visual or collision test.
"""
import bpy,random,math,sys
from mathutils.bvhtree import BVHTree
random.seed(42);poses=[]
for i in range(120):poses.append({n:tuple(random.uniform(-m,m) for m in limits) for n,limits in {'Thigh.L':(55,0,45),'Thigh.R':(55,0,45),'Shin.L':(20,0,0),'Shin.R':(20,0,0),'Torso':(0,0,15)}.items()})
# Include a separate set reaching 60 degrees in the lateral axis.
for i in range(60):poses.append({n:tuple(random.uniform(-m,m) for m in limits) for n,limits in {'Thigh.L':(40,0,60),'Thigh.R':(40,0,60),'Torso':(0,0,15)}.items()})
def tree(name):
 o=bpy.data.objects[name];e=o.evaluated_get(bpy.context.evaluated_depsgraph_get());m=e.to_mesh();t=BVHTree.FromPolygons([e.matrix_world@v.co for v in m.vertices],[p.vertices[:] for p in m.polygons],all_triangles=False,epsilon=1e-6);e.to_mesh_clear();return t
out=[]
for path in sys.argv[sys.argv.index('--')+1:]:
 bpy.ops.wm.open_mainfile(filepath=path);rig=bpy.data.objects['Draconia Body Rig - experimental'];vals=[]
 for p in poses:
  for b in rig.pose.bones:b.rotation_mode='XYZ';b.rotation_euler=(0,0,0)
  for n,v in p.items():rig.pose.bones[n].rotation_euler=[math.radians(x) for x in v]
  bpy.context.view_layer.update();l=tree('Legs');vals.append((len(tree('Cloak').overlap(l)),len(tree('Skirt').overlap(l))))
 out.append(vals);print('RANDOM',path,'cloak pairs',sum(v[0] for v in vals),'skirt pairs',sum(v[1] for v in vals),'cloak poses',sum(v[0]>0 for v in vals),flush=True)
print('CLOAK_IMPROVED',sum(a[0]>b[0] for a,b in zip(*out)),'WORSE',sum(a[0]<b[0] for a,b in zip(*out)),'MAX_INCREASE',max(b[0]-a[0] for a,b in zip(*out)),flush=True)
