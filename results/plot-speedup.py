import sys
import re
from collections import defaultdict, OrderedDict

import numpy as np
import seaborn as sns
import matplotlib.pyplot as plt
import matplotlib.colors as colors

if len(sys.argv) >= 2:
    FILE = sys.argv[1]
else:
    FILE = "20170899-final.csv"

if "-medians" in FILE:
    print("Warning: do not use -medians.csv, but original, so I can calculate error bars.")

OUTPUT_NAME = re.sub(r"(-medians)?\.csv$", "-speedup.pdf", FILE)
OUTPUT_ORIGINAL_NAME = re.sub(r"(-medians)?\.csv$", "-original-speedup.pdf", FILE)
OUTPUT_TXACT_NAME = re.sub(r"(-medians)?\.csv$", "-txact-speedup.pdf", FILE)

VERSIONS = ["original", "txact"]

# (version, s)s to plot
VSS = [ # ordered
    ("original", 1),
    ("txact", 1),
    ("txact", 2),
    ("txact", 8),
    ("txact", 64),
]

LABELS = {
    ("original", 1): "Original version",
    ("txact", 1):    "Using tx actors, s = 1",
    ("txact", 2):    "Using tx actors, s = 2",
    ("txact", 8):    "Using tx actors, s = 8",
    ("txact", 64):   "Using tx actors, s = 64",
}

# VUB orange = #FF6600 in HSV = 24,1.00,1.00
palette = [
    [ 0, 1.00, 0.25],
    [ 8, 1.00, 0.50],
    [16, 1.00, 0.75],
    [24, 1.00, 1.00],
]
palette = [colors.hsv_to_rgb([c[0] / 360.0, c[1], c[2]]) for c in palette]
COLORS = {
    ("original", 1): "#003399",
    ("txact", 1):     palette[0],
    ("txact", 2):     palette[1],
    ("txact", 8):     palette[2],
    ("txact", 64):    palette[3],
}

def parse_file(filename):
    results = defaultdict(lambda: defaultdict(list))  # version -> (w, s) -> [time]
    with open(filename, "r", encoding="utf-8") as file:
        file.readline()  # skip header
        for line in file:
            if line.strip() == "":
                continue
            try:
                version, w, s, i, time = line.split(",")
                # time is suffixed with \n
            except ValueError:
                print("Error: could not read line:\n%s" % line)
                continue
            if version not in VERSIONS:
                print("Error: version should be original or txact but is %s, "
                    "in line:\n%s" % (version, line))
                continue
            w = int(w)
            if version == "original":
                if s == "None":
                    s = 1
                else:
                    print("Error: expected s to be None for original version, "
                        "in line:\n%s" % line)
                    continue
            else:
                if s == "None":
                    print("Error: s is None, but expected a number for txact "
                        "version, in line:\n%s" % line)
                    continue
                else:
                    s = int(s)
            time = float(str(time).strip())
            results[version][(w, s)].append(time)

    quartiles_per_version = {}  # version -> (w,s) -> first|median|third -> time
    for v in VERSIONS:
        sorted_results = OrderedDict(sorted(results[v].items()))
        quartiles = OrderedDict()  # (w,s) -> first|median|third -> time
        for w_s, times in sorted_results.items():
            quartiles[w_s] = {
                "first":  np.percentile(times, 25),
                "median": np.median(times),
                "third":  np.percentile(times, 75),
            }
        quartiles_per_version[v] = quartiles

    return (quartiles_per_version["original"], quartiles_per_version["txact"])

def calculate_speedups(quartiles, base=None, version=""):
    speedups = OrderedDict()  # (w,s) -> first|median|third -> speedup
    if base is None:
        base = quartiles[(1, 1)]["median"]
    max_speedup = 1
    max_speedup_key = None
    for (w_s, quarts) in quartiles.items():
        median = base / quarts["median"]
        speedups[w_s] = {
            "first":  base / quarts["third"],
            "median": median,
            "third":  base / quarts["first"],
        }
        # Watch out: higher time is lower speedup
        # => first quartile in time is third quartile in speedup
        if median > max_speedup:
            max_speedup = median
            max_speedup_key = w_s
    print("Maximal speed-up of {} reached for {} in version {}".format(
        max_speedup, max_speedup_key, version))
    return speedups

def calculate_errors(speedups):
    errors = OrderedDict()  # (w,s) -> [error_first, error_third]
    for (w_s, quartiles) in speedups.items():
        error_first = quartiles["median"] - quartiles["first"]
        error_third = quartiles["third"] - quartiles["median"]
        errors[w_s] = [error_first, error_third]
    return errors

def draw_speedup(speedups_original, errors_original, speedups_txact, errors_txact):
    # Type 1 fonts
    plt.rcParams["ps.useafm"] = True
    plt.rcParams["pdf.use14corefonts"] = True

    sns.set_style("whitegrid", {"grid.color": ".9"})

    ax = plt.axes()
    sns.despine(top=True, right=True, left=True, bottom=True)

    w_values = sorted(set(w for (w, s) in speedups_txact.keys()))
    s_values = sorted(set(s for (v, s) in VSS))

    ax.set_xlabel(r"Number of primary worker actors ($p$)", fontsize="large")
    xticks = [x for x in w_values if (x % 4 == 0) or (x == 1)]
    ax.set_xticks(xticks)
    ax.set_xticklabels(xticks)
    ax.set_xlim(0.8, 64.2)

    ax.set_ylabel("Speed-up", fontsize="large")
    ax.set_ylim(0, 12.01)

    lines = {}

    medians_original = [quarts["median"] for quarts in speedups_original.values()]
    errors_original = np.transpose([es for es in errors_original.values()])
    lines[("original", 1)] = ax.errorbar(x=w_values, y=medians_original,
        yerr=errors_original, color=COLORS[("original", 1)])

    for s in s_values:
        medians = [speedups_txact[(w, s)]["median"] for w in w_values]
        errors = np.transpose([errors_txact[(w, s)] for w in w_values])
        lines[("txact", s)] = ax.errorbar(x=w_values, y=medians,
            yerr=errors, color=COLORS[("txact", s)])

    ax.legend([lines[vs] for vs in VSS], [LABELS[vs] for vs in VSS],
       loc="upper left", prop={"size": "small"})

    def arrowprops(n=90):
        return {
            "arrowstyle": "->",
            "color": "black",
            "connectionstyle": "angle3,angleA=0,angleB={}".format(n),
            "shrinkB": 5,
        }

    ax.annotate(xy=(1, 1), s="Original with $p = 1$:\ntime = 5656 ms",
        xytext=(5, 130), textcoords="offset points", arrowprops=arrowprops())
    ax.annotate(xy=(42, 2.5), s="Original with $p = 42$:\nspeed-up = 2.5\ntime = 2230 ms",
        xytext=(0, -50), textcoords="offset points", arrowprops=arrowprops())

    ax.annotate(xy=(1, 0.4), s="Tx act with $p = 1$, $s = 1$:\nspeed-up = 0.4\ntime = 14377 ms",
        xytext=(50, -5), textcoords="offset points", arrowprops=arrowprops(10))
    ax.annotate(xy=(38, 11.0), s="Tx act with $p = 38$, $s = 8$:\nspeed-up = 11.0\ntime = 516 ms",
        xytext=(50, -5), textcoords="offset points", arrowprops=arrowprops(90))
    # Find best speed-up for s = 1:
    # print(sorted(
    #         ((w, speedup["median"]) for ((w, s), speedup) in speedups_txact.items() if s == 1),
    #         key=lambda w_res: w_res[1], reverse=True)[0])
    ax.annotate(xy=(32, 5.9), s="Tx act with $p = 32$, $s = 1$:\nspeed-up = 6.1\ntime = 933 ms",
        xytext=(20, -55), textcoords="offset points", arrowprops=arrowprops(90))

    plt.savefig(OUTPUT_NAME, bbox_inches="tight")
    #plt.show()

(quartiles_original, quartiles_txact) = parse_file(FILE)
speedup_base = quartiles_original[(1, 1)]["median"]
speedups_original = calculate_speedups(quartiles_original, speedup_base, "original")
errors_original = calculate_errors(speedups_original)
speedups_txact = calculate_speedups(quartiles_txact, speedup_base, "txact")
errors_txact = calculate_errors(speedups_txact)
draw_speedup(speedups_original, errors_original, speedups_txact, errors_txact)
