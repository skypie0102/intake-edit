"""Sample 120 seeded arm poses and compare sleeve polygon-area ratios.

Run: blender -b --python-exit-code 1 --python tools/draconia_audit_random_sleeves.py -- BEFORE.blend AFTER.blend [SEED]
Area ratios do not establish visual quality or collision clearance.
"""
import bpy,math,random,sys
args=sys.argv[sys.argv.index('--')+1:]
if len(args) not in (2,3):raise ValueError('Expected BEFORE.blend AFTER.blend [SEED]')
random.seed(int(args[2]) if len(args)==3 else 731);poses=[]
for i in range(120):
 side='L' if i%2==0 else 'R'
 poses.append({f'UpperArm.{side}':(random.uniform(-50,50),random.uniform(-35,35),random.uniform(-50,50)),f'Forearm.{side}':(random.uniform(-60,60),random.uniform(-20,20),random.uniform(-20,20))})
def setpose(r,p):
 for b in r.pose.bones:b.rotation_mode='XYZ';b.rotation_euler=(0,0,0)
 for n,v in p.items():r.pose.bones[n].rotation_euler=[math.radians(x) for x in v]
 bpy.context.view_layer.update()
def coords(o):
 ev=o.evaluated_get(bpy.context.evaluated_depsgraph_get());m=ev.to_mesh();vs=[v.co.copy() for v in m.vertices];ev.to_mesh_clear();return vs
def area(p,v):return sum((v[p.vertices[i]]-v[p.vertices[0]]).cross(v[p.vertices[i+1]]-v[p.vertices[0]]).length*.5 for i in range(1,len(p.vertices)-1))
outputs=[]
for path in args[:2]:
 bpy.ops.wm.open_mainfile(filepath=path);o=bpy.data.objects['Cloak'];r=bpy.data.objects['Draconia Body Rig - experimental'];ids={g.index for g in o.vertex_groups if g.name.startswith(('UpperArm.','Forearm.'))};polys=[p for p in o.data.polygons if sum(sum(g.weight for g in o.data.vertices[i].groups if g.group in ids) for i in p.vertices)/len(p.vertices)>.2];setpose(r,{});v=coords(o);base=[area(p,v) for p in polys];out=[];allratios=[]
 for p in poses:
  setpose(r,p);v=coords(o);ratios=[area(poly,v)/a for poly,a in zip(polys,base) if a>1e-9];out.append((sum(x>2 for x in ratios),sum(x<.5 for x in ratios),max(ratios)));allratios.extend(ratios)
 allratios.sort();outputs.append(out);print('RANDOM',path,'above2',sum(x[0] for x in out),'below0.5',sum(x[1] for x in out),'p99',round(allratios[int(len(allratios)*.99)],3),'max',round(max(x[2] for x in out),3),flush=True)
a,b=outputs;print('POSES_IMPROVED',sum(x[0]>y[0] for x,y in zip(a,b)),'WORSE',sum(x[0]<y[0] for x,y in zip(a,b)),'MAX_INCREASE',max(y[0]-x[0] for x,y in zip(a,b)),flush=True)
