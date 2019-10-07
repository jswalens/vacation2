import sys
import re
from collections import defaultdict, OrderedDict

import numpy as np
import seaborn as sns
import matplotlib.pyplot as plt

if len(sys.argv) >= 2:
    FILE = sys.argv[1]
else:
    FILE = "20170899-final.csv"

if "-medians" in FILE:
    print("Warning: do not use -medians.csv, but original, so I can calculate error bars.")

OUTPUT_ORIGINAL_NAME = re.sub(r"(-medians)?\.csv$", "-original-speedup-graph.pdf", FILE)
OUTPUT_TXACT_NAME = re.sub(r"(-medians)?\.csv$", "-txact-speedup-graph.pdf", FILE)

VERSIONS = ["original", "txact"]

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

def calculate_speedups(quartiles, version=""):
    speedups = OrderedDict()  # (w,s) -> first|median|third -> speedup
    speedup_base = quartiles[(1, 1)]["median"]
    max_speedup = 1
    max_speedup_key = None
    for (w_s, quarts) in quartiles.items():
        median = speedup_base / quarts["median"]
        speedups[w_s] = {
            "first":  speedup_base / quarts["third"],
            "median": median,
            "third":  speedup_base / quarts["first"],
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

def draw_speedup_original(speedups, errors):
    # Type 1 fonts
    plt.rcParams["ps.useafm"] = True
    plt.rcParams["pdf.use14corefonts"] = True
    plt.rcParams["text.usetex"] = True

    sns.set_style("whitegrid", {"grid.color": ".9"})

    x_values = [w for (w, s) in speedups.keys()]

    plt.figure(figsize=(6, 2))
    ax = plt.axes()
    sns.despine(top=True, right=True, left=True, bottom=True)
    #ax.set_title("Speed-up of original version", fontsize="x-large")
    ax.set_xlabel(r"Number of worker actors ($p$)", fontsize="large")
    #ax.set_xscale("log", basex=2)
    xticks = [x for x in x_values if (x % 4 == 0) or (x == 1)]
    ax.set_xticks(xticks)
    ax.set_xticklabels(xticks)
    ax.set_ylabel("Speed-up", fontsize="large")
    ax.set_ylim(0, 3.01)
    #ax.set_yticks([0.0, 1.0, 2.0, 3.0])

    median_speedups = [quarts["median"] for quarts in speedups.values()]
    errors_ = np.transpose([es for es in errors.values()])
    line = ax.errorbar(x=x_values, y=median_speedups,
        yerr=errors_,
        #color=COLORS[variation]
        )
    ax.set_xlim(0.8, 64.2)

    arrowprops = {
        "arrowstyle": "->",
        "color": "black",
        "connectionstyle": "angle3,angleA=0,angleB=90",
        "shrinkB": 5,
    }
    ax.annotate(xy=(1, 1), s="time = 5656 ms",
        xytext=(30, -25), textcoords="offset points", arrowprops=arrowprops)
    ax.annotate(xy=(42, 2.5), s="For $p = 42$:\nspeed-up = 2.5\ntime = 2230 ms",
        xytext=(0, -60), textcoords="offset points", arrowprops=arrowprops)

    plt.savefig(OUTPUT_ORIGINAL_NAME, bbox_inches="tight")
    #plt.show()

def draw_speedup_txact(speedups, errors):
    # Type 1 fonts
    plt.rcParams["ps.useafm"] = True
    plt.rcParams["pdf.use14corefonts"] = True
    plt.rcParams["text.usetex"] = True

    sns.set_style("whitegrid", {"grid.color": ".9"})

    w_values = sorted(set(w for (w, s) in speedups.keys()))
    #s_values = sorted(set(s for (w, s) in speedups.keys()))
    s_values = [1, 2, 8, 64]

    #sns.set_palette(sns.husl_palette(len(s_values), h=50/360, l=.75, s=1))
    #sns.set_palette(sns.husl_palette(len(s_values), h=12/360, l=.5, s=1))
    sns.set_palette(sns.cubehelix_palette(len(s_values), reverse=True))

    plt.figure(figsize=(6, 3))
    ax = plt.axes()
    sns.despine(top=True, right=True, left=True, bottom=True)
    #ax.set_title("Speed-up of original version", fontsize="x-large")
    ax.set_xlabel(r"Number of primary worker actors ($p$)", fontsize="large")
    #ax.set_xscale("log", basex=2)
    xticks = [x for x in w_values if (x % 4 == 0) or (x == 1)]
    ax.set_xticks(xticks)
    ax.set_xticklabels(xticks)
    ax.set_ylabel("Speed-up", fontsize="large")
    ax.set_ylim(0, 35.01)
    lines = {}
    for s in s_values:
        x = w_values
        median_speedups = [speedups[(w, s)]["median"] for w in w_values]
        errors_ = np.transpose([errors[(w, s)] for w in w_values])
        line = ax.errorbar(x=x, y=median_speedups,
            yerr=errors_,
            #color=COLORS[s]
            )
        lines[s] = line
    ax.legend([lines[s] for s in s_values], ["$s = %i$" % s for s in s_values],
        #title=r"Secondary worker actors ($s$)",
        loc="upper left", prop={"size": "small"})
    ax.set_xlim(0.8, 64.2)

    arrowprops = {
        "arrowstyle": "->",
        "color": "black",
        "connectionstyle": "angle3,angleA=0,angleB=90",
        "shrinkB": 5,
    }
    ax.annotate(xy=(1, 1), s="For $p = 1$, $s = 1$: time = 14377 ms",
        xytext=(40, 0), textcoords="offset points", arrowprops=arrowprops)
    ax.annotate(xy=(38, 27.9), s="For $p = 38$, $s = 8$:\nspeed-up = 27.9\ntime = 516 ms",
        xytext=(40, 5), textcoords="offset points", arrowprops=arrowprops)
    # Find best speed-up for s = 1:
    # print(sorted(
    #         ((w, speedup["median"]) for ((w, s), speedup) in speedups.items() if s == 1),
    #         key=lambda w_res: w_res[1], reverse=True)[0])
    ax.annotate(xy=(32, 15.4), s="For $p = 32$, $s = 1$:\nspeed-up = 15.4\ntime = 933 ms",
        xytext=(-35, -50), textcoords="offset points", arrowprops=arrowprops)

    plt.savefig(OUTPUT_TXACT_NAME, bbox_inches="tight")
    #plt.show()

(quartiles_original, quartiles_txact) = parse_file(FILE)
speedups_original = calculate_speedups(quartiles_original, "original")
errors_original = calculate_errors(speedups_original)
draw_speedup_original(speedups_original, errors_original)

speedups_txact = calculate_speedups(quartiles_txact, "txact")
errors_txact = calculate_errors(speedups_txact)
draw_speedup_txact(speedups_txact, errors_txact)
