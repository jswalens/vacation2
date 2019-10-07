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
    OUTPUT_PDF = re.sub(r"(-medians)?\.csv$", "-txact-speedup-graph.pdf", FILE)

def parse_file(filename):
    results = defaultdict(list)  # (w, s) -> [time]
    with open(filename, "r", encoding="utf-8") as file:
        for line in file:
            if line.strip() == "":
                continue
            try:
                version, w, s, i, time = line.split(",")
                # time is suffixed with \n
            except ValueError:
                print("Error: could not read line:\n%s" % line)
            if version != "txact":
                continue
            w = int(w)
            if s != "None":
                s = int(s)
            time = float(str(time).strip())
            results[(w, s)].append(time)
    results = OrderedDict(sorted(results.items()))

    quartiles = OrderedDict()  # w -> first|median|third -> time
    for w_s, times in results.items():
        quartiles[w_s] = {
            'first':  np.percentile(times, 25),
            'median': np.median(times),
            'third':  np.percentile(times, 75),
        }

    return quartiles

def calculate_speedups(quartiles):
    speedups = OrderedDict()  # w -> first|median|third -> speedup
    speedup_base = quartiles[(1, 1)]['median']
    max_speedup = 1
    max_speedup_key = None
    for (w_s, quarts) in quartiles.items():
        median = speedup_base / quarts['median']
        speedups[w_s] = {
            'first':  speedup_base / quarts['third'],
            'median': median,
            'third':  speedup_base / quarts['first'],
        }
        # Watch out: higher time is lower speedup
        # => first quartile in time is third quartile in speedup
        if median > max_speedup:
            max_speedup = median
            max_speedup_key = w_s
    print("Maximal speed-up of {} reached for {}".format(max_speedup,
        max_speedup_key))
    return speedups

def calculate_errors(speedups):
    errors = OrderedDict()  # w -> [error_first, error_third]
    for (w_s, quartiles) in speedups.items():
        error_first = quartiles["median"] - quartiles["first"]
        error_third = quartiles["third"] - quartiles["median"]
        errors[w_s] = [error_first, error_third]
    return errors

def draw_speedup(speedups, errors):
    # Type 1 fonts
    plt.rcParams['ps.useafm'] = True
    plt.rcParams['pdf.use14corefonts'] = True
    plt.rcParams['text.usetex'] = True

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
    #ax.set_title("Speed-up of original version", fontsize='x-large')
    ax.set_xlabel(r"Number of primary worker actors ($p$)", fontsize='large')
    #ax.set_xscale("log", basex=2)
    xticks = [x for x in w_values if (x % 4 == 0) or (x == 1)]
    ax.set_xticks(xticks)
    ax.set_xticklabels(xticks)
    ax.set_ylabel("Speed-up", fontsize='large')
    ax.set_ylim(0, 35.01)
    lines = {}
    for s in s_values:
        x = w_values
        median_speedups = [speedups[(w, s)]['median'] for w in w_values]
        errors_ = np.transpose([errors[(w, s)] for w in w_values])
        line = ax.errorbar(x=x, y=median_speedups,
            yerr=errors_,
            #color=COLORS[s]
            )
        lines[s] = line
    ax.legend([lines[s] for s in s_values], ["$s = %i$" % s for s in s_values],
        #title=r"Secondary worker actors ($s$)",
        loc='upper left', prop={'size': 'small'})
    ax.set_xlim(0.8, 64.2)

    ax.annotate(xy=(1, 1), s="For $p = 1$, $s = 1$: time = 13701 ms",
        xytext=(40, 0), textcoords='offset points',
        arrowprops=dict(arrowstyle="->"))

    ax.annotate(xy=(46, 17.2), s="For $p = 46$, $s = 1$:\nspeed-up = 18.4\ntime = 743 ms",
        xytext=(-35, -50), textcoords='offset points',
        arrowprops=dict(arrowstyle="->"))

    ax.annotate(xy=(42, 33.3), s="For $p = 42$, $s = 8$:\nspeed-up = 33.2\ntime = 413 ms",
        xytext=(-130, -15), textcoords='offset points',
        arrowprops=dict(arrowstyle="->"))

    plt.savefig(OUTPUT_PDF, bbox_inches='tight')
    #plt.show()

quartiles = parse_file(FILE)
speedups = calculate_speedups(quartiles)
errors = calculate_errors(speedups)
draw_speedup(speedups, errors)
