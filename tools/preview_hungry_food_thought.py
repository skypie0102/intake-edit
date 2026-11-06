"""Hungry idle: a daydream of warm bread interrupts his reading."""
import math
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
import generate_companion_rig as rig
N=rig.N
FRAMES=196
FRAME_MS=80

def window(t,start,rise,end,fall):
    return rig.smooth((t-start)/rise)*rig.smooth((end-t)/fall)

def make_frame(t):
    if t >= 1: t=0
    reading=window(t,.01,.08,.28,.13)
    dreaming=window(t,.18,.15,.72,.16)
    slump=window(t,.64,.12,.95,.14)
    blink=(.155<=t<.165 or .75<=t<.76)
    root=ET.fromstring(rig.svg_for('hungry',['head_group','left_arm','right_arm','book'],
        phase=0,idle_phase=0,view_box='92 87 320 320',blinking=blink))
    head=root[1]
    head.set('transform',f'translate(0 {3*reading-3*dreaming+5*slump:.3f}) rotate({4*dreaming-3*slump:.3f} 252 270)')
    eyes=head[1][0]
    eyes.set('transform',eyes.get('transform','')+f' translate({-5*dreaming:.3f} {3*reading-4*dreaming+2*slump:.3f})')
    # Let the book and both hands sink together as his attention wanders.
    sink=window(t,.18,.18,.88,.16)
    book=ET.Element(N+'g',{'transform':f'translate(0 {10*sink:.3f})'})
    for node in list(root)[2:]:
        root.remove(node); book.append(node)
    root.append(book)
    # A separate visual daydream: warm bread rather than the existing rice-bowl bubble.
    opacity=window(t,.27,.08,.69,.10)
    if opacity > .001:
        float_y=2*math.sin((t-.27)*math.pi*4)
        g=ET.SubElement(root,N+'g',{'opacity':f'{opacity:.3f}','transform':f'translate(0 {float_y:.2f})'})
        ET.SubElement(g,N+'path',{'d':'M114 192 C111 181 117 171 131 169 C140 161 151 165 155 171 C168 171 173 182 170 192 Q168 199 159 200 L125 200 Q116 199 114 192 Z','fill':'#dca36b','stroke':'#926779','stroke-width':'3','stroke-linejoin':'round'})
        ET.SubElement(g,N+'path',{'d':'M125 181 Q132 176 136 176 M141 178 Q148 173 152 175 M154 182 Q160 179 163 180','fill':'none','stroke':'#f8dcb2','stroke-width':'3','stroke-linecap':'round'})
        steam=ET.SubElement(g,N+'g',{'transform':f'translate(0 {-3*rig.smooth((t-.30)/.30):.2f})'})
        ET.SubElement(steam,N+'path',{'d':'M129 163 Q124 158 129 152 M142 159 Q137 153 142 147 M155 163 Q150 158 155 152','fill':'none','stroke':'#e7c5d5','stroke-width':'2.5','stroke-linecap':'round'})
    return ET.tostring(root,encoding='unicode')

def main():
    out=Path(sys.argv[1]); out.mkdir(parents=True,exist_ok=True)
    for i in range(FRAMES): (out/f'{i:03}.svg').write_text(make_frame(i/(FRAMES-1)),encoding='utf8')
if __name__ == '__main__': main()
