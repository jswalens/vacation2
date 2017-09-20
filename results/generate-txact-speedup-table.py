import sys
import subprocess
import re

if len(sys.argv) >= 2:
    FILE = sys.argv[1]
else:
    FILE = "20170899-final-medians.csv"

if len(sys.argv) >= 3:
    OUTPUT_PDF = sys.argv[2]
else:
    OUTPUT_PDF = re.sub(r"(-medians)?\.csv$", "-txact-speedup-table.pdf", FILE)

OUTPUT_TIKZ = re.sub(r"\.pdf$", ".tikz", OUTPUT_PDF)

TEMPLATE = r"""
\documentclass[tikz]{standalone}

\usepackage{libertine}
\renewcommand{\familydefault}{\sfdefault}
\usepackage{color}
\usepackage{xcolor}

\definecolor{text-color}{cmyk}{0,0,0,1.0}
%(colors)s

\usetikzlibrary{calc,matrix}

\begin{document}
\begin{tikzpicture}

\matrix (m) [matrix of nodes,text opacity=0.8,color=text-color,every node/.style={inner xsep=0em,inner ysep=0.25em,outer sep=0em,minimum width=2.05em}]
{
%(matrix)s
};

\node[above=1.5ex] at ($(m-1-2)!0.5!(m-1-33)$){Number of primary worker actors ($p$)};
\node[rotate=90] at ($(m-2-1)!0.5!(m-33-1)+(-3.5ex,0)$) {Number of secondary worker actors ($s$)};

\end{tikzpicture}
\end{document}
"""

COLOR = r"\definecolor{cell-%(w)s-%(s)s}{cmyk}{%(color)s}"
CELL = r"|[fill=cell-%(w)s-%(s)s]| %(value).1f"

def parse_file(filename):
    results = {}  # (w, s) -> time
    w_values = set()
    s_values = set()
    with open(filename, "r", encoding="utf-8") as file:
        for line in file:
            if line.strip() == "":
                continue
            try:
                version, w, s, time = line.split(",")
                # time is suffixed with \n
            except ValueError:
                print("Error: could not read line:\n%s" % line)
            if version != "txact":
                continue
            w = int(w)
            if s != "None":
                s = int(s)
            time = float(str(time).strip())
            results[(w, s)] = time
            w_values.add(w)
            s_values.add(s)
    w_values = sorted(w_values) # set -> sorted list
    s_values = sorted(s_values)
    return (results, w_values, s_values)

def calculate_speedups(results):
    speedups = {}
    speedup_base = results[(1, 1)]
    for (k, v) in results.items():
        speedups[k] = speedup_base / v
    return speedups

def generate_color(speedup, max, w, s):
    ratio = speedup / max
    ratio_sq = ratio * ratio
    color = "%.5f,%.5f,%.5f,%.5f" % (0.7*ratio_sq, 0, 0.85*ratio_sq + 0.15, 0)
    return COLOR % {"w": w, "s": s, "color": color}

def generate_colors(results):
    m = max(results.values())
    colors = [generate_color(speedup, m, w, s)
        for ((w, s), speedup) in results.items()]
    return "\n".join(colors)

def label(text):
    return r"|[text opacity=1]| " + str(text)

def line(cells):
    return " & ".join(cells) + r" \\"

def generate_matrix(speedups, w_values, s_values):
    lines = []
    lines.append(line([""] + [label(w) for w in w_values]))
    for s in s_values:
        cells = [CELL % {"w": w, "s": s, "value": speedups[(w, s)]}
            for w in w_values]
        lines.append(line([label(s)] + cells))
    return "\n".join(lines)  # There should NOT be a newline after the last line

(results, w_values, s_values) = parse_file(FILE)
speedups = calculate_speedups(results)
colors = generate_colors(speedups)
matrix = generate_matrix(speedups, w_values, s_values)
out = TEMPLATE % {"colors": colors, "matrix": matrix}
with open(OUTPUT_TIKZ, "w") as f:
    f.write(out)
subprocess.call(["pdflatex", OUTPUT_TIKZ])
