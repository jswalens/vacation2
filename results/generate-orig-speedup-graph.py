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

if len(sys.argv) >= 3:
    OUTPUT_PDF = sys.argv[2]
else:
    OUTPUT_PDF = re.sub(r"(-medians)?\.csv$", "-orig-speedup-graph.pdf", FILE)

def parse_file(filename):
    results = defaultdict(list)  # w -> [time]
    with open(filename, "r", encoding="utf-8") as file:
        for line in file:
            if line.strip() == "":
                continue
            try:
                version, w, s, i, time = line.split(",")
                # time is suffixed with \n
            except ValueError:
                print("Error: could not read line:\n%s" % line)
            if version != "orig":
                continue
            w = int(w)
            if s != "None":
                print("Error: expected s None on line:\n%s" % line)
            time = float(str(time).strip())
            results[w].append(time)
    results = OrderedDict(sorted(results.items()))

    quartiles = OrderedDict()  # w -> first|median|third -> time
    for k, times in results.items():
        quartiles[k] = {
            'first':  np.percentile(times, 25),
            'median': np.median(times),
            'third':  np.percentile(times, 75),
        }

    return quartiles

def calculate_speedups(quartiles):
    speedups = OrderedDict()  # w -> first|median|third -> speedup
    speedup_base = quartiles[1]['median']
    for (w, quarts) in quartiles.items():
        speedups[w] = {
            'first':  speedup_base / quarts['third'],
            'median': speedup_base / quarts['median'],
            'third':  speedup_base / quarts['first'],
        }
        # Watch out: higher time is lower speedup
        # => first quartile in time is third quartile in speedup
    return speedups

def calculate_errors(speedups):
    errors = OrderedDict()  # w -> [error_first, error_third]
    for (w, quartiles) in speedups.items():
        error_first = quartiles["median"] - quartiles["first"]
        error_third = quartiles["third"] - quartiles["median"]
        errors[w] = [error_first, error_third]
    return errors

def draw_speedup(speedups, errors):
    # Type 1 fonts
    plt.rcParams['ps.useafm'] = True
    plt.rcParams['pdf.use14corefonts'] = True
    plt.rcParams['text.usetex'] = True

    sns.set_style("whitegrid", {"grid.color": ".9"})

    plt.figure(figsize=(6, 2))
    ax = plt.axes()
    sns.despine(top=True, right=True, left=True, bottom=True)
    #ax.set_title("Speed-up of original version", fontsize='x-large')
    ax.set_xlabel(r"Number of worker actors ($p$)", fontsize='large')
    #ax.set_xscale("log", basex=2)
    xticks = [x for x in speedups.keys() if (x % 4 == 0) or (x == 1)]
    ax.set_xticks(xticks)
    ax.set_xticklabels(xticks)
    ax.set_ylabel("Speed-up", fontsize='large')
    ax.set_ylim(0, 3.01)
    #ax.set_yticks([0.0, 1.0, 2.0, 3.0])
    x = [t for t in speedups.keys()]
    median_speedups = [quarts['median'] for quarts in speedups.values()]
    errors_ = np.transpose([es for es in errors.values()])
    line = ax.errorbar(x=x, y=median_speedups,
        yerr=errors_,
        #color=COLORS[variation]
        )
    ax.set_xlim(0.8, 64.2)

    ax.annotate(xy=(1, 1), s="time = 5480 ms",
        xytext=(20, -20), textcoords='offset points',
        arrowprops=dict(arrowstyle="->"))

    ax.annotate(xy=(42, 2.55), s="For $p = 42$:\nspeed-up = 2.6\ntime = 2102 ms",
        xytext=(0, -45), textcoords='offset points',
        arrowprops=dict(arrowstyle="->"))

    plt.savefig(OUTPUT_PDF, bbox_inches='tight')
    #plt.show()

quartiles = parse_file(FILE)
speedups = calculate_speedups(quartiles)
errors = calculate_errors(speedups)
draw_speedup(speedups, errors)
