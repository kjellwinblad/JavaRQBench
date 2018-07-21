import re
import matplotlib
import sys

matplotlib.rcParams['pdf.fonttype'] = 42
matplotlib.rcParams['ps.fonttype'] = 42

data_files=sys.argv[1:]

matplotlib.rcParams.update({'font.size': 7})

matplotlib.use('Agg')
import matplotlib.pyplot as plt
import sys

xticks = [1,2,4,8,16,32,64]
xticks_labels = map(lambda x: str(x), xticks)

measurmentPoints = 3

def readFile(the_file, column=1):
    values = []
    with open(the_file, 'r') as f:
        lines = f.readlines()
        #lines = lines[2:]
        for l in lines:
            cols=l.split(" ")
            values.append(float(cols[column]))
    return values

def plotCase(file_prefix, column, label, marker, ax, color, filter_points=[]):
    suffix = "_1"
    xvals = readFile(file_prefix + suffix, 0)
    x = []
    yvals = []
    yvals_time = []
    y = []
    y_error_mins = []
    y_error_maxs = []
    for i in range(0, measurmentPoints):
        yvals.append(readFile(file_prefix + suffix, i*8+4))
    for i in range(0, measurmentPoints):
        yvals_time.append(readFile(file_prefix + suffix, i*8+3))
    for line in range(0, len(xvals)):
        currentX = xvals[line]
        if currentX in filter_points:
            continue
        x.append(currentX)
        measurments = []
        for i in range(0, measurmentPoints):
            measurments.append((float(yvals[i][line]) / float(yvals_time[i][line]))*1000)
        average = sum(measurments)/measurmentPoints
        y.append(average)
        y_error_mins.append(average - min(measurments))
        y_error_maxs.append(max(measurments) -average)
    xticks = [1,2,4,8,16,32,64,128]
    xticklabels = ["1","2","4","8","16","32","64", "128"]
    ax.set_xscale('log', basex=2)
    ax.set_xticks( xticks )
    ax.set_xticklabels( xticklabels )
    return ax.errorbar(x,y,[y_error_mins,y_error_maxs],label=label,linewidth=1, elinewidth=1,marker=marker,color=color)



def draw_graph(out_file,
               graph_title,
               legend = False,
               yaxis_max = 0,
               base_line = 0):
    plt.figure(figsize=(2.4,2.4))
    plt.xlabel('Number of Threads')
    if legend:
        plt.ylabel('Throughput (ops/$\mu$s)')
    labels = []
    for (file_prefix, label, marker, color) in table_types_and_names:
        plotCase(file_prefix,
                 1, label, marker, plt.gca(), color, filter_points=[15,24,48,64])
    if legend:
        plt.legend(loc=0, ncol=1, framealpha=0.5)
    if yaxis_max != 0:
        plt.ylim((0,yaxis_max))
    if base_line != 0:
        ypos=float(number_of_nodes) / (base_line * 1000000)
        plt.plot([0, 64], [ypos, ypos], color='k', linestyle='--')
    plt.axvline(x=64,c="gray",ymin=0,ymax=1.0,linewidth=2,zorder=0,clip_on=True,linestyle=':')
    plt.savefig(out_file + '.pdf', bbox_inches='tight', pad_inches = 0)


#plot different range size graphs
for set_size in [1000000]: #100000,,10000000
    for (reads,range_queries,range_query_max_size,no1,no2, show_y_label) in [(0.5,0.0,0,0.0,0, True),
                                                                             (0.8,0.0,0,0.0,0, True),
                                                                             (0.99,0.0,0,0.0,0, True),
                                                                             (0.55,0.25,10,0.0,0, True),
                                                                             #(0.55,0.25,100,0.0,0),
                                                                             (0.55,0.25,1000,0.0,0, True),
                                                                             #(0.55,0.25,10000,0.0,0),
                                                                             (0.55,0.25,100000,0.0,0, True),
                                                                             #(0.55,0.25,1000000,0.0,0),
                                                                             #(0.59,0.01,10000,0.0,0),
                                                                             # (0.0,1.0,64000,0.0,0),
                                                                             # (0.0,0.0,0,0.0,0),
                                                                             # (1.0,0.0,0,0.0,0),
                                                                             # (0.5,0.0,0,0.0,0),
                                                                             # (0.8,0.0,0,0.0,0)
    ]:
        print((reads,range_queries,range_query_max_size,no1,no2))
        table_types_and_names = [("./no@3@10@3@10@ALL@"+str(set_size)+"@"+str(int(set_size/2))+"@"+str(reads)+"@"+str(range_queries)+"@"+str(range_query_max_size)+"@0.0@0_" + "se.uu.collection.KiWiRangeQueryMap", "KiWi", 'o', '#C96565'),
                                 ("./no@3@10@3@10@ALL@"+str(set_size)+"@"+str(int(set_size/2))+"@"+str(reads)+"@"+str(range_queries)+"@"+str(range_query_max_size)+"@0.0@0_" + "algorithms.published.LockFreeKSTRQ", "k-ary", 'd', '#00BD06'),
                                 ("./no@3@10@3@10@ALL@"+str(set_size)+"@"+str(int(set_size/2))+"@"+str(reads)+"@"+str(range_queries)+"@"+str(range_query_max_size)+"@0.0@0_" + "se.uu.collection.RangeUpdateSnapTree", "SnapTree", '*', '#377339'),
                                 ("./no@3@10@3@10@ALL@"+str(set_size)+"@"+str(int(set_size/2))+"@"+str(reads)+"@"+str(range_queries)+"@"+str(range_query_max_size)+"@0.0@0_" + "se.uu.collection.LockFreeRangeCollectorSkipList", "ChatterjeeSL", '+', '#d67724'),
                                 ("./no@3@10@3@10@ALL@"+str(set_size)+"@"+str(int(set_size/2))+"@"+str(reads)+"@"+str(range_queries)+"@"+str(range_query_max_size)+"@0.0@0_" + "se.uu.collection.NonAtomicRangeUpdateConcurrentSkipListMap", "NonAtomicSL", 'p', '#B971D1'),
                                 ("./no@3@10@3@10@ALL@"+str(set_size)+"@"+str(int(set_size/2))+"@"+str(reads)+"@"+str(range_queries)+"@"+str(range_query_max_size)+"@0.0@0_" + "se.uu.collection.ImmTreapCoarseMap", "Im-Tr-Coarse", 'x', '#B0B0B0'),
                                 ("./no@3@10@3@10@ALL@"+str(set_size)+"@"+str(int(set_size/2))+"@"+str(reads)+"@"+str(range_queries)+"@"+str(range_query_max_size)+"@0.0@0_" + "se.uu.collection.CATreeMapSTDR", "AVL-CA", '^', '#4052A3'),
                                 ("./no@3@10@3@10@ALL@"+str(set_size)+"@"+str(int(set_size/2))+"@"+str(reads)+"@"+str(range_queries)+"@"+str(range_query_max_size)+"@0.0@0_" + "se.uu.collection.FatCATreeMapSTDR", "SL-CA", '>', '#7189F5'),
                                 ("./no@3@10@3@10@ALL@"+str(set_size)+"@"+str(int(set_size/2))+"@"+str(reads)+"@"+str(range_queries)+"@"+str(range_query_max_size)+"@0.0@0_" + "se.uu.collection.ImmTreapCATreeMapSTDR", "CA tree (locks)", '<', '#666699'),
                                 ("./no@3@10@3@10@ALL@"+str(set_size)+"@"+str(int(set_size/2))+"@"+str(reads)+"@"+str(range_queries)+"@"+str(range_query_max_size)+"@0.0@0_" + "se.uu.collection.LockFreeImmTreapCATreeMapSTDR", "LFCA tree", '*', '#000000')]
        yaxis_max = 0
        draw_graph("./no@3@10@3@10@ALL@"+str(set_size)+"@"+str(int(set_size/2))+"@"+str(reads)+"@"+str(range_queries)+"@"+str(range_query_max_size)+"@0.0@0",
                   graph_title = "./no@3@10@3@10@ALL@"+str(set_size)+"@"+str(int(set_size/2))+"@"+str(reads)+"@"+str(range_queries)+"@"+str(range_query_max_size)+"@0.0@0",
                   legend = show_y_label,
                   yaxis_max = yaxis_max)

