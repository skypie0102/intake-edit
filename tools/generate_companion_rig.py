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
        lids = ET.Element(N+'path', {'d':'M214 225 Q224 230 234 225 M271 225 Q281 230 291 225',
                                     'fill':'none','stroke':'#493552','stroke-width':'5','stroke-linecap':'round'})
        blink = body_part([lids])
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
    writing = smooth((phase - .30) / .40)
    return reach, writing


def writing_arm(reach, writing):
    # The same single limb morphs from the approved raised arm to the page.
    start = (305,254, 318,247,324,231,327,220, 329,210,337,207,343,212,
             352,220,346,232,341,241, 337,249,333,256,328,264)
    end = (305,254, 316,258,325,260,333,267, 340,274,341,280,337,285,
           334,290,327,296,321,296, 313,296,307,293,302,288)
    vals = [a+(b-a)*reach for a,b in zip(start,end)]
    # A small horizontal hand movement follows the writing tip.
    travel = 25 * writing * reach
    # The wrist and hand travel together; the shoulder remains connected.
    for i, weight in zip(range(2, 26, 2), (.15, .35, .5, .7, .85, 1, 1, 1, 1, 1, 1, 1)):
        vals[i] += travel * weight
    return ('M%.2f %.2f C%.2f %.2f %.2f %.2f %.2f %.2f '
            'C%.2f %.2f %.2f %.2f %.2f %.2f C%.2f %.2f %.2f %.2f %.2f %.2f '
            'C%.2f %.2f %.2f %.2f %.2f %.2f Q%.2f %.2f %.2f %.2f Z') % tuple(vals + [((vals[24]+vals[0])/2)*(1-reach) + (296 + travel*.55)*reach, ((vals[25]+vals[1])/2)*(1-reach) + 270*reach, vals[0], vals[1]])


def ink_point(progress):
    """Tip and visible ink use the same curved path across the page."""
    return 297 + 25*progress, 315 - 5*progress + 1.2*math.sin(6*math.pi*progress)


def pen_position(reach, writing):
    target_x, target_y = ink_point(writing)
    # Follow the hand from the raised pose to the writing pose.
    return (326 + (target_x - 326)*reach, 257 + (target_y - 257)*reach)


def add_writing(root, phase, layer):
    reach, writing = writing_pose(phase)
    if layer == 'ink' and .30 <= phase < .94:
        points = [ink_point(i / 48) for i in range(int(writing * 48) + 1)]
        points.append(ink_point(writing))
        path = 'M' + ' L'.join(f'{x:.2f} {y:.2f}' for x, y in points)
        ET.SubElement(root, N+'path', {'d':path,
            'fill':'none','stroke':'#85617a','stroke-width':'2','stroke-linecap':'round','stroke-linejoin':'round'})
    if layer == 'pen' and .15 < phase < .94:
        x, y = pen_position(reach, writing)
        # The shaft follows the existing hand as it moves.
        ET.SubElement(root, N+'path', {'d':f'M{x:.2f} {y:.2f} L{x+21:.2f} {y-38:.2f}',
            'fill':'none','stroke':'#654e70','stroke-width':'4','stroke-linecap':'round'})
        ET.SubElement(root, N+'circle', {'cx':f'{x:.2f}','cy':f'{y:.2f}','r':'1.3','fill':'#a98458'})


def happy_focus(phase):
    # Look down before the pen touches the page, then look up when finished.
    return smooth((phase - .08) / .18) * smooth((.84 - phase) / .16)


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


def hungry_action(phase):
    look = smooth((phase - .12) / .18) * smooth((.67 - phase) / .18)
    sigh = smooth((phase - .61) / .12) * smooth((.94 - phase) / .15)
    return look, sigh


def hungry_facial_layers(layers, look, blinking):
    head = deepcopy(layers['head'])
    eyes = deepcopy(layers['blink' if blinking else 'eyes'])
    for node in head[0]:
        path = node.get('d', '')
        if path.startswith('M242 250 Q252 240 262 250'):
            # Keep his mouth downturned while his eyes look up.
            node.set('d', f'M242 {250-look:.2f} Q252 {240+3*look:.2f} 262 {250-look:.2f}')
        elif path.startswith('M215 197 L230 201'):
            node.set('d', f'M215 {197-2*look:.2f} L230 {201-3*look:.2f} '
                          f'M275 {201-3*look:.2f} L290 {197-2*look:.2f}')
    if not blinking:
        for node in eyes[0]:
            if node.tag == N+'ellipse':
                node.set('cy', f'{225-4*look:.2f}')
                node.set('ry', f'{3+2*look:.2f}')
            elif node.tag == N+'path':
                node.set('d', f'M211 {219-2*look:.2f} Q224 {223-5*look:.2f} 237 {219-2*look:.2f} '
                              f'M268 {219-2*look:.2f} Q281 {223-5*look:.2f} 294 {219-2*look:.2f}')
    return head, eyes


def sad_action(phase):
    # A hesitant glance up, then a small retreat towards the book.
    glance = smooth((phase - .12) / .20) * smooth((.62 - phase) / .17)
    settle = smooth((phase - .57) / .14) * smooth((.94 - phase) / .17)
    tear = smooth((phase - .51) / .10) * smooth((.86 - phase) / .13)
    return glance, settle, tear


def sad_facial_layers(layers, glance, tear, blinking):
    head = deepcopy(layers['head'])
    eyes = deepcopy(layers['blink' if blinking else 'eyes'])
    for node in head[0]:
        path = node.get('d', '')
        if path.startswith('M215 231 C206 247'):
            # A single cheek tear appears briefly, without covering the monocle.
            node.set('d', path.split(' M293', 1)[0])
            node.set('opacity', f'{tear:.3f}')
            node.set('transform', f'translate(0 {3*tear:.2f})')
        elif path.startswith('M211 207 Q222 212'):
            node.set('d', f'M211 {207-2*glance:.2f} Q222 {212-2*glance:.2f} 232 205 '
                          f'M271 205 Q281 {212-2*glance:.2f} 293 {207-2*glance:.2f}')
    if not blinking:
        for node in eyes[0]:
            if node.tag in (N+'ellipse', N+'circle'):
                node.set('cy', f'{float(node.get("cy"))-3*glance:.2f}')
    return head, eyes


def waiting_idle(phase):
    """A seamless, restrained breath and a glance toward the page."""
    breath = math.sin(2 * math.pi * phase)
    glance = smooth((phase - .04) / .12) * smooth((.36 - phase) / .13)
    return breath, glance


def waiting_follow_through(phase):
    """Small cyclic offsets that lag behind the body's vertical motion."""
    lag = math.sin(2 * math.pi * phase - .6) + math.sin(.6)
    return 1.1 * lag, 1.2 * lag


def waiting_head(layers, phase):
    head = deepcopy(layers['head'])
    tuft, _ = waiting_follow_through(phase)
    silhouette = head[0][0]
    path = silhouette.get('d')
    original = 'C228 146 231 123 230 113 C250 132 254 142 253 148 C269 134 271 119 269 107 C285 120 288 136 286 153'
    replacement = (f'C228 146 {231+tuft:.2f} 123 {230+tuft:.2f} 113 '
                   f'C{250+tuft*.7:.2f} 132 254 142 253 148 '
                   f'C269 134 {271+tuft:.2f} 119 {269+tuft:.2f} 107 '
                   f'C{285+tuft*.7:.2f} 120 288 136 286 153')
    if original not in path:
        raise ValueError('Waiting silhouette no longer matches the approved tuft')
    silhouette.set('d', path.replace(original, replacement, 1))
    return head


def svg_for(mood, selected, progress=0, blinking=False, show_symbol=False, phase=None, idle_phase=None):
    original, art, layers = parts(mood)
    root = svg_root(original)
    for part in selected:
        if part == 'head_group':
            focus = happy_focus(phase) if mood == 'happy' and phase is not None else 0
            look, sigh = hungry_action(phase) if mood == 'hungry' and phase is not None else (0, 0)
            glance, settle, tear = sad_action(phase) if mood == 'sad' and phase is not None else (0, 0, 0)
            breath, page_glance = waiting_idle(idle_phase if idle_phase is not None else phase) if mood == 'waiting' and phase is not None else (0, 0)
            transform = (f'translate(0 {-7*progress:.2f})' if mood == 'new'
                         else f'translate(0 {-3*look + 2*sigh:.2f})' if mood == 'hungry'
                         else f'translate(0 {-3*glance + 2*settle:.2f})' if mood == 'sad'
                         else f'translate(0 {-1.2*breath:.2f}) rotate({1.5*progress:.2f} 252 270)' if mood == 'waiting' and phase is not None
                         else f'rotate({1.5*progress + 2*focus:.2f} 252 270)')
            group = ET.SubElement(root,N+'g',{'transform':transform})
            if mood == 'happy' and phase is not None:
                head, eyes = happy_facial_layers(layers, focus, blinking)
                group.append(head)
                group.append(eyes)
            elif mood == 'hungry' and phase is not None:
                head, eyes = hungry_facial_layers(layers, look, blinking)
                group.append(head)
                group.append(eyes)
            elif mood == 'sad' and phase is not None:
                head, eyes = sad_facial_layers(layers, glance, tear, blinking)
                group.append(head)
                group.append(eyes)
            elif mood == 'waiting' and phase is not None:
                group.append(waiting_head(layers, idle_phase if idle_phase is not None else phase))
                eyes = deepcopy(layers['blink' if blinking else 'eyes'])
                if not blinking:
                    current = eyes[0].get('transform', '')
                    eyes[0].set('transform', f'{current} translate(0 {3*page_glance:.2f})')
                group.append(eyes)
            else:
                group.append(deepcopy(layers['head']))
                group.append(deepcopy(layers['blink' if blinking else 'eyes']))
        elif part == 'ink' and mood == 'happy' and phase is not None:
            add_writing(root, phase, 'ink')
        elif part == 'pen' and mood == 'happy' and phase is not None:
            add_writing(root, phase, part)
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
        elif part == 'symbol' and mood == 'hungry' and phase is not None:
            # Thought bubbles lead to a small bowl of food, only during the action.
            if .20 <= phase < .72:
                for threshold, x, y, radius in ((.20,333,224,2), (.26,340,214,3), (.32,347,203,4)):
                    if phase >= threshold:
                        ET.SubElement(root, N+'circle', {'cx':str(x),'cy':str(y),'r':str(radius),
                            'fill':'#fbf5ef','stroke':'#b7a6c5','stroke-width':'1.5'})
                if phase >= .38:
                    group = ET.SubElement(root, N+'g', {'opacity':f'{smooth((.72-phase)/.08):.3f}'})
                    ET.SubElement(group, N+'path', {'d':'M343 171 C340 159 350 152 359 154 C366 146 377 150 380 156 C392 155 397 164 392 173 C397 181 391 190 382 191 C375 198 365 196 360 192 C349 196 340 187 343 179 C340 176 341 173 343 171 Z',
                        'fill':'#fbf5ef','stroke':'#b7a6c5','stroke-width':'2.5','stroke-linejoin':'round'})
                    if phase >= .44:
                        # Rice mound and bowl, sized to remain legible in the widget.
                        ET.SubElement(group, N+'path', {'d':'M352 174 Q355 167 361 169 Q366 163 371 169 Q377 167 381 174 Z',
                            'fill':'#ffffff','stroke':'#ae9aa8','stroke-width':'1.5','stroke-linejoin':'round'})
                        ET.SubElement(group, N+'path', {'d':'M351 174 Q354 187 365 188 Q377 188 382 174 Z',
                            'fill':'#d78b7d','stroke':'#8b6272','stroke-width':'2','stroke-linejoin':'round'})
                        ET.SubElement(group, N+'path', {'d':'M354 177 Q366 181 379 177 M360 189 L374 189',
                            'fill':'none','stroke':'#f5dbb8','stroke-width':'1.6','stroke-linecap':'round'})
                    if phase >= .50:
                        ET.SubElement(group, N+'path', {'d':'M360 165 Q357 162 360 159 M370 165 Q367 162 370 159',
                            'fill':'none','stroke':'#b7a6c5','stroke-width':'1.5','stroke-linecap':'round'})
            _, sigh = hungry_action(phase)
            if sigh > .01:
                # Two soft breath marks rise briefly while he settles back.
                group = ET.SubElement(root, N+'g', {'transform':f'translate({-2*sigh:.2f} {-5*sigh:.2f})',
                                                   'opacity':f'{.75*sigh:.3f}'})
                ET.SubElement(group, N+'path', {'d':'M146 226 q-8 9 0 18 q8 9 0 18 M132 235 q-6 7 0 14',
                    'fill':'none', 'stroke':'#b7a6c5', 'stroke-width':'4', 'stroke-linecap':'round'})
        elif part == 'symbol' and mood == 'happy' and phase is not None:
            # Each sparkle grows and shrinks around its own centre with staggered timing.
            if .65 <= phase < .93:
                big, dot, small = layers['symbol']
                left_path, right_path = small.get('d').split(' M', 1)
                left, right = deepcopy(small), deepcopy(small)
                left.set('d', left_path)
                right.set('d', 'M' + right_path)
                for index, (node, x, y, start) in enumerate((
                    (big, 354, 182, .65), (left, 149, 247, .69),
                    (right, 367, 234, .73), (dot, 147, 195, .77))):
                    envelope = smooth((phase-start)/.06) * smooth((.93-phase)/.08)
                    pulse = 1 + .18*math.sin((phase-start)*math.pi*12 + index*.6)
                    size = max(0, envelope*pulse)
                    if size > .001:
                        group = ET.SubElement(root, N+'g', {
                            'transform':f'translate({x} {y}) scale({size:.3f}) translate({-x} {-y})'})
                        group.append(deepcopy(node))
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
        elif part == 'book' and mood == 'waiting' and phase is not None:
            nodes = deepcopy(layers[part])
            _, ribbon = waiting_follow_through(idle_phase if idle_phase is not None else phase)
            # Rotate only the ribbon, behind the stable book, around its hidden attachment.
            nodes[0].set('transform', f'rotate({ribbon:.2f} 313 341)')
            for node in nodes: root.append(node)
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
