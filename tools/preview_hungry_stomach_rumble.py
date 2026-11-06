"""Hungry idle: his stomach interrupts his reading."""
import math
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
import generate_companion_rig as rig

N = rig.N
FRAMES = 200
FRAME_MS = 80

def window(t, start, rise, end, fall):
    return rig.smooth((t-start)/rise) * rig.smooth((end-t)/fall)

def hand_path(amount):
    # One continuous arm, smoothly bent inward toward the tummy.
    rest = (303,269, 303,255,310,245,322,247, 334,249,338,259,330,272)
    touch = (303,269, 300,246,286,231,275,231, 264,230,259,240,265,249)
    v = [a+(b-a)*amount for a,b in zip(rest,touch)]
    return ('M%.2f %.2f C%.2f %.2f %.2f %.2f %.2f %.2f '
            'C%.2f %.2f %.2f %.2f %.2f %.2f Z') % tuple(v)

def make_frame(t):
    if t >= 1: t = 0
    read = window(t,.01,.07,.28,.12)
    reaction = window(t,.27,.045,.40,.08)
    discomfort = window(t,.31,.12,.79,.17)
    hand = window(t,.36,.16,.77,.16)
    slump = window(t,.56,.14,.92,.13)
    blink = .15 <= t < .16 or .77 <= t < .78
    root = ET.fromstring(rig.svg_for('hungry', ['head_group','left_arm','right_arm','book'],
        phase=0,idle_phase=0,view_box='92 87 320 320',blinking=blink))
    head = root[1]
    head.set('transform',f'translate(0 {-4*reaction+5*discomfort+2*slump:.3f}) rotate({-3*reaction+4*discomfort:.3f} 252 270)')
    eyes = head[1][0]
    scan = (rig.smooth((t-.03)/.12)*5-2.5)*read
    eyes.set('transform',eyes.get('transform','')+f' translate({scan:.3f} {3*read+5*discomfort:.3f})')
    mouth = next(n for n in head[0][0] if n.get('d','').startswith('M242 250'))
    mouth.set('d',f'M242 250 Q252 {240-2*discomfort:.3f} 262 250')
    right = root[3][0][0]
    right.set('d',hand_path(hand))
    # Lower the book and the attached arms together; the hand curls inward.
    lowered = window(t,.28,.13,.88,.17)
    book = ET.Element(N+'g',{'transform':f'translate(0 {12*lowered:.3f})'})
    for n in list(root)[2:]:
        root.remove(n); book.append(n)
    root.append(book)
    # Two small staggered vibration marks pulse and fade; no screen or body fade.
    pulse = window(t,.27,.035,.43,.10)
    if pulse > .001:
        for i in range(2):
            a = pulse * (.65 + .35*math.sin((t-.27)*math.pi*15+i))
            ET.SubElement(root,N+'path',{'d': 'M164 256 q-7 -7 0 -14 q7 -7 0 -14' if i == 0 else 'M152 260 q-5 -6 0 -12',
                'fill':'none','stroke':'#c8aacf','stroke-width':'4','stroke-linecap':'round','opacity':f'{max(0,a):.3f}'})
    return ET.tostring(root,encoding='unicode')

def main():
    out=Path(sys.argv[1]);out.mkdir(parents=True,exist_ok=True)
    for i in range(FRAMES):
        (out/f'{i:03}.svg').write_text(make_frame(i/(FRAMES-1)),encoding='utf8')
if __name__ == '__main__': main()
