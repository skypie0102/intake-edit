"""Compare sleeve polygon-area distortion at two corrective-smoothing strengths.

Run: blender -b RIG.blend --python-exit-code 1 --python tools/draconia_audit_sleeves.py
Area ratios are a narrow diagnostic and do not detect all clipping.
"""
import bpy,math
r=bpy.data.objects['Draconia Body Rig - experimental'];objects=[bpy.data.objects[n] for n in ['Cloak','Jacket Hands']]
poses=[]
for side in ('L','R'):
 for axis in (0,1,2):
  for a in (-50,0,50):
   for b in (-60,0,60):
    v=[0,0,0];v[axis]=a;poses.append((f'{side}_{axis}_{a}_{b}',{f'UpperArm.{side}':v,f'Forearm.{side}':(b,0,0)}))
for a in (-40,40):
 for b in (-45,45):poses.append((f'both_{a}_{b}',{'UpperArm.L':(0,0,a),'Forearm.L':(b,0,0),'UpperArm.R':(0,0,-a),'Forearm.R':(-b,0,0)}))
def pose(p):
 for b in r.pose.bones:b.rotation_mode='XYZ';b.rotation_euler=(0,0,0)
 for name,angles in p.items():r.pose.bones[name].rotation_euler=[math.radians(a) for a in angles]
 bpy.context.view_layer.update()
def vertices(obj):
 dg=bpy.context.evaluated_depsgraph_get();o=obj.evaluated_get(dg);m=o.to_mesh();v=[x.co.copy() for x in m.vertices];o.to_mesh_clear();return v
def area(p,v):return sum((v[p.vertices[i]]-v[p.vertices[0]]).cross(v[p.vertices[i+1]]-v[p.vertices[0]]).length*.5 for i in range(1,len(p.vertices)-1))
def mask(o):
 ids={g.index for g in o.vertex_groups if g.name.startswith(('UpperArm.','Forearm.'))};return [p for p in o.data.polygons if sum(sum(g.weight for g in o.data.vertices[i].groups if g.group in ids) for i in p.vertices)/len(p.vertices)>.2]
for o in objects:
 polys=mask(o);results=[]
 for factor in (.35,.5):
  o.modifiers['Sleeve corrective smoothing'].factor=factor;pose({});neutral=vertices(o);base=[area(p,neutral) for p in polys];out={};allr=[]
  for name,p in poses:
   pose(p);v=vertices(o);rs=[area(poly,v)/a for poly,a in zip(polys,base) if a>1e-9];rs.sort();out[name]=(max(rs),sum(x>2 for x in rs));allr.extend(rs)
  allr.sort();results.append(out);print(o.name,factor,'p95',round(allr[int(.95*len(allr))],3),'p99',round(allr[int(.99*len(allr))],3),'above2',sum(x[1] for x in out.values()),'max',round(max(x[0] for x in out.values()),3),flush=True)
 for name in results[0]:
  a,b=results[0][name],results[1][name]
  if b[1]>a[1]:print('REGRESSION',o.name,name,a,b,flush=True)
