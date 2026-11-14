"""Build an experimental texture-masked blink; not a full eyelid rig.

blender -b --python-exit-code 1 --python tools/draconia_add_blink_study.py -- INPUT.blend OUTPUT.blend
The face has eye whites painted into its texture. This visual approximation is
Blender-material dependent and needs visual review at different views/sizes.
"""
import sys
from pathlib import Path
args=sys.argv[sys.argv.index('--')+1:]
if len(args)!=2:raise ValueError('Expected INPUT.blend OUTPUT.blend')
source,output=(Path(p).resolve() for p in args)
if source==output or output.exists():raise ValueError('Output must be a new file')
import bpy
bpy.ops.wm.open_mainfile(filepath=str(source))
from pathlib import Path
from mathutils import Vector
r=bpy.data.objects['Draconia Body Rig - experimental'];r['Blink']=0.0;r.id_properties_ui('Blink').update(min=0,max=1,description='Close the eyes')
for name in ('Circle','Circle.001','Circle.002'):
 o=bpy.data.objects[name];basis=o.data.shape_keys.key_blocks['Basis']
 for fcurve in o.data.shape_keys.animation_data.drivers:
  if 'Gaze_' in fcurve.data_path:
   blink_var=fcurve.driver.variables.new();blink_var.name='closing';blink_var.type='SINGLE_PROP';blink_var.targets[0].id=r;blink_var.targets[0].data_path='["Blink"]';fcurve.driver.expression='('+fcurve.driver.expression+')*(1-closing)'
 key=o.shape_key_add(name='Blink',from_mix=False)
 for side in (-1,1):
  ids=[v.index for v in o.data.vertices if (o.matrix_world@basis.data[v.index].co).x*side>0]
  if not ids:continue
  z=(min(basis.data[i].co.z for i in ids)+max(basis.data[i].co.z for i in ids))/2
  for i in ids:key.data[i].co.z=z+(basis.data[i].co.z-z)*.01
 d=key.driver_add('value').driver;d.type='SCRIPTED';v=d.variables.new();v.name='blink';v.type='SINGLE_PROP';v.targets[0].id=r;v.targets[0].data_path='["Blink"]';d.expression='blink'
m=bpy.data.materials['Head'];nt=m.node_tree;tex=nt.nodes['Image Texture'];em=nt.nodes['Sketchfab-style unlit color'];sep=nt.nodes.new('ShaderNodeSeparateColor');nt.links.new(tex.outputs['Color'],sep.inputs['Color'])
def mathnode(op,a,b=None):
 n=nt.nodes.new('ShaderNodeMath');n.operation=op
 if hasattr(a,'node'):nt.links.new(a,n.inputs[0])
 else:n.inputs[0].default_value=a
 if b is not None:
  if hasattr(b,'node'):nt.links.new(b,n.inputs[1])
  else:n.inputs[1].default_value=b
 return n.outputs[0]
delta=mathnode('ABSOLUTE',mathnode('SUBTRACT',sep.outputs['Red'],sep.outputs['Green']));low=mathnode('LESS_THAN',delta,.025);high=mathnode('GREATER_THAN',sep.outputs['Green'],.65);mask=mathnode('MULTIPLY',low,high);value=nt.nodes.new('ShaderNodeValue');value.name='Blink control';d=value.outputs[0].driver_add('default_value').driver;d.type='SCRIPTED';v=d.variables.new();v.name='blink';v.type='SINGLE_PROP';v.targets[0].id=r;v.targets[0].data_path='["Blink"]';d.expression='blink';fac=mathnode('MULTIPLY',mask,value.outputs[0]);mix=nt.nodes.new('ShaderNodeMixRGB');mix.blend_type='MIX';nt.links.new(fac,mix.inputs[0]);nt.links.new(tex.outputs['Color'],mix.inputs[1]);
def linear(v):
 v=v/255
 return v/12.92 if v<=.04045 else ((v+.055)/1.055)**2.4
mix.inputs[2].default_value=(*[linear(v) for v in (234,215,207)],1);nt.links.new(mix.outputs[0],em.inputs['Color'])

r["Blink"]=0.0
r.update_tag()
bpy.context.view_layer.update()
bpy.ops.wm.save_as_mainfile(filepath=str(output))
print("BLINK_STUDY_CREATED",flush=True)
