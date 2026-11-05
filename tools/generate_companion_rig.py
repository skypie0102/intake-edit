"""Generate native vector parts and widget poses from the approved SVG.

The widget uses precomputed poses because launchers host RemoteViews. The app
animates the same named parts continuously. No bitmap generation is required.
"""
from copy import deepcopy
from pathlib import Path
import xml.etree.ElementTree as ET
import math
from svg_to_android import svg_to_vector

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'app/src/main/assets/companion/approved_state_concepts.svg'
OUTPUT = ROOT / 'app/src/main/res/drawable'
SVG = 'http://www.w3.org/2000/svg'
N = '{' + SVG + '}'
ET.register_namespace('', SVG)
MOODS = ('new', 'happy', 'waiting', 'hungry', 'sad')
PARTS = ('head', 'eyes', 'blink', 'left_arm', 'right_arm', 'symbol')
POSES = (0, 0, .1, .3, .55, .8, 1, 1, 1, 1, 1, 1, 1, .9, .6, 0)
PHASES = (0, .04, .09, .14, .19, .24, .29, .34, .40, .48, .56, .64, .72, .8, .9, 0)
GREETING_POSES = (0, 0, .1, .3, .55, .8, 1, 1, 1, 1, 1, 1, .9, .65, .3, 0)
DOTS = ((329, 207), (339, 195), (348, 181))
QUESTION_CURVE = 'M352 161 Q348 152 352 144 Q356 136 367 137 Q381 138 382 150 Q382 160 369 166 Q363 169 364 176'

def question_length():
    points = [(352,161), (348,152), (352,144), (356,136), (367,137),
              (381,138), (382,150), (382,160), (369,166), (363,169), (364,176)]
    length = 0
    for i in range(0, 10, 2):
        a, b, c = points[i:i+3]
        previous = a
        for step in range(1, 101):
            t = step / 100
            point = tuple((1-t)**2*a[k] + 2*(1-t)*t*b[k] + t*t*c[k] for k in (0, 1))
            length += math.dist(previous, point)
            previous = point
    return length

QUESTION_LENGTH = question_length()

# One continuous limb; both poses have the same cubic segments and attachment.
ARM_REST = (303,269, 303,255,310,245,322,247, 330,248,335,253,334,262, 334,266,332,270,330,272)
ARM_THINK = (303,269, 293,263,282,256,277,248, 272,241,265,239,262,245, 259,251,266,264,282,273)


def arm_path(progress):
    if not 0 <= progress <= 1:
        raise ValueError('arm progress must be between zero and one')
    v = [a + (b-a)*progress for a,b in zip(ARM_REST, ARM_THINK)]
    return (('M%.2f %.2f C%.2f %.2f %.2f %.2f %.2f %.2f '
             'C%.2f %.2f %.2f %.2f %.2f %.2f C%.2f %.2f %.2f %.2f %.2f %.2f') % tuple(v)
            + ' Z')


def scene(mood):
    original = ET.parse(SOURCE).getroot()
    scenes = [child for child in original if child.tag == N+'svg']
    return original, scenes[MOODS.index(mood)]


def svg_root(original):
    root = ET.Element(N+'svg', {'width':'320','height':'320','viewBox':'97 92 310 310'})
    root.append(deepcopy(original[0]))
    return root


def parts(mood):
    original, art = scene(mood)
    body = art[0][0]
    elements = list(body)
    eye_ids = {'new':{2,3,4,5}, 'happy':{2,3,4,5}, 'waiting':{2,3,4,5},
               'hungry':{2,3,4}, 'sad':{3,4,5,6}}[mood]

    def body_part(selected):
        wrapper = ET.Element(N+'g', {'clip-path':'url(#behind-book)'})
        group = ET.SubElement(wrapper, N+'g', {'transform':body.get('transform','')})
        for child in selected: group.append(deepcopy(child))
        return wrapper

    head = body_part([child for i,child in enumerate(elements[:-1]) if i not in eye_ids])
    eyes = body_part([child for i,child in enumerate(elements[:-1]) if i in eye_ids])
    if mood == 'hungry':
        blink = deepcopy(eyes)  # This mood is already half-lidded.
    else:
        y = 223 if mood == 'sad' else 218 if mood == 'happy' else 219
        lids = ET.Element(N+'path', {'d':f'M214 {y} Q224 {y+9} 234 {y} M271 {y} Q281 {y+9} 291 {y}',
                                      'fill':'none','stroke':'#493552','stroke-width':'5','stroke-linecap':'round'})
        blink = body_part([lids])
    arm = elements[-1]
    left, right = arm.get('d').split(' M',1)
    # Keep the approved limb shapes; lower each one so the book hides its base.
    left_node = deepcopy(arm); left_node.set('d', left + ' Z')
    right_node = deepcopy(arm); right_node.set('d', 'M' + right + ' Z')
    if mood == 'waiting': right_node.set('d', arm_path(0))
    for node in (left_node, right_node):
        node.set('transform', 'translate(0 10)')
    return original, art, {'head':head, 'eyes':eyes, 'blink':blink,
                           'left_arm':body_part([left_node]), 'right_arm':body_part([right_node]),
                           'symbol':[deepcopy(x) for x in list(art)[7:]],
                           'book':[deepcopy(x) for x in list(art)[1:7]]}


def smooth(value):
    value = max(0, min(1, value))
    return value * value * (3 - 2 * value)


def writing_pose(phase):
    """Return reach and pen travel; the existing hand replaces its resting pose."""
    reach = smooth((phase - .10) / .18) * smooth((.94 - phase) / .18)
    writing = smooth((phase - .30) / .35)
    return reach, writing


def writing_arm(reach, writing):
    # The same single limb morphs from the approved raised arm to the page.
    start = (305,254, 318,247,324,231,327,220, 329,210,337,207,343,212,
             352,220,346,232,341,241, 337,249,333,256,328,264)
    end = (305,254, 316,258,325,260,333,267, 340,274,341,280,337,285,
           334,290,327,296,321,296, 313,296,307,293,302,288)
    vals = [a+(b-a)*reach for a,b in zip(start,end)]
    # A small horizontal hand movement follows the writing tip.
    for i in (14,16,18,20,22,24): vals[i] += 12*writing*reach
    return ('M%.2f %.2f C%.2f %.2f %.2f %.2f %.2f %.2f '
            'C%.2f %.2f %.2f %.2f %.2f %.2f C%.2f %.2f %.2f %.2f %.2f %.2f '
            'C%.2f %.2f %.2f %.2f %.2f %.2f Q%.2f %.2f %.2f %.2f Z') % tuple(vals + [((vals[24]+vals[0])/2)*(1-reach) + 296*reach, ((vals[25]+vals[1])/2)*(1-reach) + 270*reach, vals[0], vals[1]])


def add_writing(root, phase, layer):
    reach, writing = writing_pose(phase)
    if layer == 'ink' and .3 <= phase < .9:
        x, y = 296 + 12*writing, 315 - 3*writing
        ET.SubElement(root, N+'path', {'d':f'M296 315 L{x:.2f} {y:.2f}',
            'fill':'none','stroke':'#85617a','stroke-width':'2','stroke-linecap':'round'})
    if layer == 'pen' and .20 < phase < .9:
        # The shaft is held in the moving hand; its tip touches the new ink.
        x, y = 296 + 12*writing, 315 - 3*writing
        lift = 20 * (1-reach)
        ET.SubElement(root, N+'path', {'d':f'M{x:.2f} {y-lift:.2f} L{x+18:.2f} {y-30-lift:.2f}',
            'fill':'none','stroke':'#654e70','stroke-width':'4','stroke-linecap':'round'})
        ET.SubElement(root, N+'circle', {'cx':f'{x:.2f}','cy':f'{y-lift:.2f}','r':'1.3','fill':'#a98458'})


def happy_focus(phase):
    # Look down before the pen touches the page, then look up when finished.
    return smooth((phase - .08) / .18) * smooth((.80 - phase) / .18)


def happy_facial_layers(layers, focus, blinking):
    head = deepcopy(layers['head'])
    eyes = deepcopy(layers['blink' if blinking else 'eyes'])
    for node in head[0]:
        path = node.get('d', '')
        if path.startswith('M239 239 Q252 245 265 239'):
            start = (239,239,252,245,265,239,263,257,252,258,241,257,239,239)
            end = (242,245,252,250,262,245,259,250,252,250,245,250,242,245)
            v = [a + (b-a)*focus for a,b in zip(start,end)]
            node.set('d', ('M%.2f %.2f Q%.2f %.2f %.2f %.2f '
                           'Q%.2f %.2f %.2f %.2f Q%.2f %.2f %.2f %.2f Z') % tuple(v))
        elif path.startswith('M245 251 Q252 247 259 251'):
            node.set('opacity', f'{1-focus:.3f}')
    # The pupils and their highlights move as a unit; the monocle stays on the head.
    eyes[0].set('transform', f'translate({3*focus:.2f} {7*focus:.2f})')
    return head, eyes


def svg_for(mood, selected, progress=0, blinking=False, show_symbol=False, phase=None):
    original, art, layers = parts(mood)
    root = svg_root(original)
    for part in selected:
        if part == 'head_group':
            focus = happy_focus(phase) if mood == 'happy' and phase is not None else 0
            transform = (f'translate(0 {-7*progress:.2f})' if mood == 'new'
                         else f'rotate({1.5*progress + 2*focus:.2f} 252 270)')
            group = ET.SubElement(root,N+'g',{'transform':transform})
            if mood == 'happy' and phase is not None:
                head, eyes = happy_facial_layers(layers, focus, blinking)
                group.append(head)
                group.append(eyes)
            else:
                group.append(deepcopy(layers['head']))
                group.append(deepcopy(layers['blink' if blinking else 'eyes']))
        elif part == 'ink' and mood == 'happy' and phase is not None:
            add_writing(root, phase, 'ink')
        elif part == 'pen' and mood == 'happy' and phase is not None:
            add_writing(root, phase, 'pen')
        elif part == 'right_arm' and mood == 'happy' and phase is not None:
            arm = deepcopy(layers[part])
            reach, writing = writing_pose(phase)
            arm[0][0].set('d', writing_arm(reach, writing))
            root.append(arm)
        elif part == 'right_arm' and mood == 'waiting' and progress:
            arm = deepcopy(layers[part])
            arm[0][0].set('d',arm_path(progress))
            root.append(arm)
        elif part == 'symbol' and show_symbol:
            for node in layers[part]: root.append(deepcopy(node))
        elif part == 'symbol' and mood == 'happy' and phase is not None:
            if .66 <= phase < .82:
                for node in layers['symbol']:
                    root.append(deepcopy(node))
        elif part == 'symbol' and mood == 'new' and phase is not None:
            # Draw the approved exclamation only while he greets the viewer.
            if .34 <= phase < .8:
                draw = max(0, min(1, (phase - .34) / .18))
                if draw > 0:
                    ET.SubElement(root, N+'path', {'d':f'M364 139 L364 {139 + 32*draw:.2f}',
                        'fill':'none', 'stroke':'#dcc2ec', 'stroke-width':'6', 'stroke-linecap':'round'})
                if phase >= .52:
                    ET.SubElement(root, N+'circle', {'cx':'364', 'cy':'185.5', 'r':'3', 'fill':'#dcc2ec'})
        elif part == 'symbol' and mood == 'waiting' and phase is not None:
            if phase < .85:
                for index, (x, y) in enumerate(DOTS):
                    if phase >= (.14, .24, .34)[index]:
                        ET.SubElement(root, N+'circle', {'cx':str(x), 'cy':str(y), 'r':'2.5', 'fill':'#dcc2ec'})
                draw = max(0, min(1, (phase - .40) / .24))
                if draw > 0:
                    ET.SubElement(root, N+'path', {'d':QUESTION_CURVE, 'fill':'none', 'stroke':'#dcc2ec',
                        'stroke-width':'5', 'stroke-linecap':'round', 'stroke-linejoin':'round',
                        'stroke-dasharray':f'{QUESTION_LENGTH:.3f} {QUESTION_LENGTH:.3f}', 'stroke-dashoffset':f'{QUESTION_LENGTH*(1-draw):.3f}', 'data-trim':str(draw)})
                if phase >= .64:
                    ET.SubElement(root, N+'circle', {'cx':'364', 'cy':'186.5', 'r':'2.5', 'fill':'#dcc2ec'})
        elif part == 'symbol':
            continue  # Symbols appear only with their corresponding actions.
        elif isinstance(layers[part], list):
            for node in layers[part]: root.append(deepcopy(node))
        else:
            root.append(deepcopy(layers[part]))
    return ET.tostring(root,encoding='unicode')


def write_vector(path, svg):
    path.write_text(svg_to_vector(svg),encoding='utf-8')


def generate():
    OUTPUT.mkdir(parents=True,exist_ok=True)
    for mood in MOODS:
        for part in PARTS:
            write_vector(OUTPUT/f'companion_rig_{mood}_{part}.xml',svg_for(mood,[part],show_symbol=part == 'symbol'))
        indices = range(len(POSES)) if mood in ('waiting', 'new') else (0, 3)
        for i in indices:
            amount = POSES[i]
            progress = amount if mood == 'waiting' else GREETING_POSES[i] if mood == 'new' else 0
            svg = svg_for(mood,['head_group','left_arm','right_arm','book','symbol'],progress,blinking=i==3,phase=PHASES[i] if mood in ('waiting', 'new') else None)
            write_vector(OUTPUT/f'companion_widget_{mood}_{i:02d}.xml',svg)
    write_vector(OUTPUT/'companion_rig_book.xml',svg_for('waiting',['book']))


if __name__ == '__main__':
    generate()
    print('Generated companion rig parts and widget poses from the approved SVG.')
