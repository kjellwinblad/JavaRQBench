#!/usr/bin/python
import re
import matplotlib
import sys
from math import log10, floor
matplotlib.rcParams['pdf.fonttype'] = 42
matplotlib.rcParams['ps.fonttype'] = 42

data_files=sys.argv[1:]

matplotlib.rcParams.update({'font.size': 7})

matplotlib.use('Agg')
import matplotlib.pyplot as plt
import sys

xticks = [1,2,4,8,16,32,64]
xticks_labels = map(lambda x: str(x), xticks)

measurmentPoints = 10

def round_sig_str(x, sig=2):
    if x == 0:
        return "0"    
    if x >= 999.5 :
        x = x/1000
        res = round(x, sig-int(floor(log10(abs(x))))-1)
        return str(res) + "K"
    elif x >= 10:
        res = round(x, sig-int(floor(log10(abs(x))))-1)
        return str(int(res))
    else:
        res = round(x, sig-int(floor(log10(abs(x))))-1)
        return str(res)

def readFile(the_file, column=1, average_tree_files=True, average_files=5):
    values = []
    if average_files > 0:
        files_lines = []
        the_file_base = the_file[:-1]
        for i in range(1,average_files+1):
            files_lines.append(open(the_file_base + str(i)).readlines())
        for i in range(0,len(files_lines[0])):
            sumation = 0
            for f in range(0,average_files):
                cols = files_lines[f][i].split(" ")
                sumation = sumation + float(cols[column])
            average = sumation / average_files
            values.append(average)
    else:
        the_file = the_file[:-1] + "1"
        with open(the_file, 'r') as f:
            lines = f.readlines()
            for l in lines:
                cols=l.split(" ")
                values.append(float(cols[column]))
    return values

# column 3
second_last_number_of_route_nodes = 0
last_number_of_route_nodes = 0

def plotCase(file_prefix, column, label, marker, color, filter_points=[],divide_by_number_of_queries=True,divide_by_number_of_milliseconds=False,append_to_column=0,roundup=True,only_this_timeperiod=False,divide_by_number_of_microseconds=False,xmax=3,addtotime=0,ax=None,secondrun=False):
    global last_number_of_route_nodes
    global second_last_number_of_route_nodes
    suffix = None
    extra_fields = 0
    if secondrun:
        suffix = "_two1"
    else:
        suffix = "_1"
    label = "LFCA tree"
    extra_fields = 2
    xvals = readFile(file_prefix + suffix, 0)
    x = []
    nr_of_range_queries = []
    nr_of_nanoseconds = []
    yvals = []
    yvals_time = []
    y = []
    y_error_mins = []
    y_error_maxs = []
    if append_to_column == 3:
        x.append(addtotime/1000)
    if append_to_column == 3:
        y.append(second_last_number_of_route_nodes)
    print(label + " & ", end = '')
    for i in range(0, measurmentPoints):
        yvals.append(readFile(file_prefix + suffix, i*(6+extra_fields)+(5+append_to_column)))
    for i in range(0, measurmentPoints):
        yvals_time.append(readFile(file_prefix + suffix, i*(6+extra_fields)+3))
    for i in range(0, measurmentPoints):
        nr_of_range_queries.append(readFile(file_prefix + suffix, i*(6+extra_fields)+5))
    for i in range(0, measurmentPoints):
        nr_of_nanoseconds.append(readFile(file_prefix + suffix, i*(6+extra_fields)+(3)))
    for line in range(0, len(xvals)):
        currentX = xvals[line]
        measurments = []
        times=[]
        for i in range(0, measurmentPoints):
            times.append(float(nr_of_nanoseconds[i][line])/float(1000000))
            value=0
            range_queries=0
            time=0
            if only_this_timeperiod and line > 0:
                time_now=nr_of_nanoseconds[i][line]
                time_prev=nr_of_nanoseconds[i][line-1]
                time=time_now-time_prev
                value_now=yvals[i][line]
                value_prev=yvals[i][line-1]
                value=value_now-value_prev
                range_queries_now=nr_of_range_queries[i][line]
                range_queries_prev=nr_of_range_queries[i][line-1]
                range_queries=range_queries_now-range_queries_prev
            else:
                time=nr_of_nanoseconds[i][line]
                value=yvals[i][line]
                range_queries=nr_of_range_queries[i][line]
            if divide_by_number_of_queries:
                measurments.append((float(value) / float(range_queries)))
            elif divide_by_number_of_milliseconds:
                micros = float(time)/float(1000000)
                measurments.append((float(value) / micros))
            elif divide_by_number_of_microseconds:
                micros = float(time)/float(1000)
                measurments.append((float(value) / micros))
            else:
                measurments.append(float(value))
        x.append((sum(times)/measurmentPoints + addtotime)/1000)
        average = sum(measurments)/measurmentPoints
        if append_to_column == 3:
            second_last_number_of_route_nodes = last_number_of_route_nodes
            last_number_of_route_nodes = average
        y.append(average)
    if (not roundup) or (append_to_column > 3 and (not divide_by_number_of_milliseconds)):
        print(" & ".join(map(lambda x: str(x), y)), end = '')
    else:
        print(" & ".join(map(lambda x: round_sig_str(x, sig=2), y)), end = '')
    print("\\\\")
    print("plotting")
    if ax != None:
        ax.plot(x,y,
                color=color,
                marker="*",
                linewidth=1)



def draw_graph(out_file,
               graph_title,
               legend = False,
               yaxis_max = 0,
               base_line = 0,
               divide_by_number_of_queries=True,
               divide_by_number_of_milliseconds=False,
               divide_by_number_of_microseconds=False,
               append_to_column=0,
               roundup=True,
               only_this_timeperiod=False,
               show_graph=False,
               ylabel='Throughput (operations/μs)',
               show_y_label=False,
               xmax=2,
               addtotime=0,
               ax=None,
               secondrun=False):
    if show_y_label:
        if ax != None:
            ax.set_ylabel(ylabel)
    labels = []
    for (file_prefix, label, marker, color) in table_types_and_names:
        plotCase(file_prefix,
                 1, label, marker, color, filter_points=[15,24,48,63,80,100],append_to_column=append_to_column, divide_by_number_of_queries=divide_by_number_of_queries,divide_by_number_of_milliseconds=divide_by_number_of_milliseconds,roundup=roundup,only_this_timeperiod=only_this_timeperiod,divide_by_number_of_microseconds=divide_by_number_of_microseconds,xmax=xmax,addtotime=addtotime,ax=ax,secondrun=secondrun)
    if yaxis_max != 0:
        if ax != None:
            ax.set_ylim(ymax=yaxis_max)

f, axarr = plt.subplots(2, 5)

for set_size in [1000000]:
    print("SSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSS")
    print("SSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSS")
    print("SSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSS")
    print(str(set_size))
    print("SSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSS")
    print("SSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSS")
    print("SSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSS")
    column = -1
    showLegend=True
    for (reads,range_queries,range_query_max_size,no1,no2,from_range_size,ymaxroutenodes, ymaxthrouput, show_y_label, xmax,addtotime,secondrun) in [
            (0.55,0.25,1000,0.0,0,0, 1560, 17.5, True, 3,0,False),
            (0.55,0.25,10,0.0,0,1000, 1560, 17.5, False, 3,2400,False),
            (0.55,0.25,1000,0.0,0,10, 1560, 17.5, False, 3,4800,False),
            (0.55,0.25,10,0.0,0,1000, 1560, 17.5, False, 3,7200,True),
            (0.55,0.25,100000,0.0,0,10, 1560, 0, False, 3,9600,False)]:
        column = column + 1
        print("##########################################")
        print("##########################################")
        print("##########################################")
        print((reads,range_queries,range_query_max_size,no1,no2,from_range_size, ymaxroutenodes))
        print("##########################################")
        print("##########################################")
        print("##########################################")
        table_types_and_names = [("./time_series@no@3035@2@10@TIME_SERIES@ALL@"+str(set_size)+"@"+str(int(set_size/2))+"@"+str(reads)+"@"+str(range_queries)+"@"+str(range_query_max_size)+"@0.0@0@"+str(from_range_size)+"_" + "se.uu.collection.LockFreeImmTreapCATreeMapSTDR", "LFCA tree", '*', '#000000')]
        yaxis_max = 0

        print("Times")
        print("1 2 4 8 16 32 64 128")
        draw_graph("./no@3@10@3@10@ALL@"+str(set_size)+"@"+str(int(set_size/2))+"@"+str(reads)+"@"+str(range_queries)+"@"+str(range_query_max_size)+"@0.0@0",
                   graph_title = "./no@3@10@3@10@ALL@"+str(set_size)+"@"+str(int(set_size/2))+"@"+str(reads)+"@"+str(range_queries)+"@"+str(range_query_max_size)+"@0.0@0",
                   legend = showLegend,
                   yaxis_max = yaxis_max,
                   append_to_column =-2,
                   divide_by_number_of_milliseconds=False,
                   divide_by_number_of_queries=False,
                   roundup=False)
        print("Ops")
        print("1 2 4 8 16 32 64 128")
        draw_graph("./no@3@10@3@10@ALL@"+str(set_size)+"@"+str(int(set_size/2))+"@"+str(reads)+"@"+str(range_queries)+"@"+str(range_query_max_size)+"@0.0@0",
                   graph_title = "./no@3@10@3@10@ALL@"+str(set_size)+"@"+str(int(set_size/2))+"@"+str(reads)+"@"+str(range_queries)+"@"+str(range_query_max_size)+"@0.0@0",
                   legend = showLegend,
                   yaxis_max = yaxis_max,
                   append_to_column =-1,
                   divide_by_number_of_milliseconds=False,
                   divide_by_number_of_queries=False,
                   roundup=False)
        print("Ops/ms")
        print("1 2 4 8 16 32 64 128")
        draw_graph("./no@3@10@3@10@ALL@"+str(set_size)+"@"+str(int(set_size/2))+"@"+str(reads)+"@"+str(range_queries)+"@"+str(range_query_max_size)+"@0.0@0",
                   graph_title = "./no@3@10@3@10@ALL@"+str(set_size)+"@"+str(int(set_size/2))+"@"+str(reads)+"@"+str(range_queries)+"@"+str(range_query_max_size)+"@0.0@0",
                   legend = showLegend,
                   yaxis_max = yaxis_max,
                   append_to_column =-1,
                   divide_by_number_of_milliseconds=True,
                   divide_by_number_of_queries=False,
                   roundup=True)
        print("Ops/ms only this time period")
        print("1 2 4 8 16 32 64 128")
        draw_graph("./000throuputtimeseriesno@30@2@3@10@ALL@"+str(set_size)+"@"+str(int(set_size/2))+"@"+str(reads)+"@"+str(range_queries)+"@"+str(range_query_max_size)+"@0.0@0",
                   graph_title = "./000smallthrouputtimeseriesno@3035@2@10@TIMESERIES@ALL@"+str(set_size)+"@"+str(int(set_size/2))+"@"+str(reads)+"@"+str(range_queries)+"@"+str(range_query_max_size)+"@0.0@0@"+str(from_range_size),
                   legend = showLegend,
                   yaxis_max = ymaxthrouput,
                   append_to_column =-1,
                   divide_by_number_of_queries=False,
                   roundup=True,
                   only_this_timeperiod=True,
                   show_graph=True,
                   divide_by_number_of_microseconds=True,
                   show_y_label=show_y_label,
                   xmax=xmax,
                   ylabel='Throughput (ops/$\mu$s)',
                   addtotime=addtotime,
                   ax=axarr[1, column],
                   secondrun=secondrun)
        print("Keys traversed per range query")
        print("1 2 4 8 16 32 64 128")
        draw_graph("./no@3@10@3@10@ALL@"+str(set_size)+"@"+str(int(set_size/2))+"@"+str(reads)+"@"+str(range_queries)+"@"+str(range_query_max_size)+"@0.0@0",
                   graph_title = "./no@3@10@3@10@ALL@"+str(set_size)+"@"+str(int(set_size/2))+"@"+str(reads)+"@"+str(range_queries)+"@"+str(range_query_max_size)+"@0.0@0",
                   legend = showLegend,
                   yaxis_max = yaxis_max,
                   append_to_column =1,
                   divide_by_number_of_milliseconds=False,
                   divide_by_number_of_queries=True)
        print("traversed nodes per range query")
        print("1 2 4 8 16 32 64 128")
        draw_graph("./no@3@10@3@10@ALL@"+str(set_size)+"@"+str(int(set_size/2))+"@"+str(reads)+"@"+str(range_queries)+"@"+str(range_query_max_size)+"@0.0@0",
                   graph_title = "./no@3@10@3@10@ALL@"+str(set_size)+"@"+str(int(set_size/2))+"@"+str(reads)+"@"+str(range_queries)+"@"+str(range_query_max_size)+"@0.0@0",
                   legend = showLegend,
                   yaxis_max = yaxis_max,
                   append_to_column =2,
                   divide_by_number_of_milliseconds=False,
                   divide_by_number_of_queries=True)
        print("number of route nodes")
        print("1 2 4 8 16 32 64 128")
        draw_graph("./000routenodestimeseriesno@30@30@2@30@ALL@"+str(set_size)+"@"+str(int(set_size/2))+"@"+str(reads)+"@"+str(range_queries)+"@"+str(range_query_max_size)+"@0.0@0",
                   graph_title = "./000smallroutenodestimeseriesno@3035@2@10@TIMESERIES@ALL@"+str(set_size)+"@"+str(int(set_size/2))+"@"+str(reads)+"@"+str(range_queries)+"@"+str(range_query_max_size)+"@0.0@0@"+str(from_range_size),
                   legend = showLegend,
                   yaxis_max = ymaxroutenodes,
                   append_to_column =3,
                   divide_by_number_of_milliseconds=False,
                   divide_by_number_of_queries=False,
                   show_graph=True,
                   show_y_label=show_y_label,
                   ylabel="Number of Route Nodes",
                   xmax=xmax,
                   addtotime=addtotime,
                   ax=axarr[0, column],
                   secondrun=secondrun)
        print("Joins")
        print("1 2 4 8 16 32 64 128")
        draw_graph("./no@3@10@3@10@ALL@"+str(set_size)+"@"+str(int(set_size/2))+"@"+str(reads)+"@"+str(range_queries)+"@"+str(range_query_max_size)+"@0.0@0",
                   graph_title = "./no@3@10@3@10@ALL@"+str(set_size)+"@"+str(int(set_size/2))+"@"+str(reads)+"@"+str(range_queries)+"@"+str(range_query_max_size)+"@0.0@0",
                   legend = showLegend,
                   yaxis_max = yaxis_max,
                   append_to_column =4,
                   divide_by_number_of_milliseconds=False,
                   divide_by_number_of_queries=False)
        print("Splits")
        draw_graph("./no@3@10@3@10@ALL@"+str(set_size)+"@"+str(int(set_size/2))+"@"+str(reads)+"@"+str(range_queries)+"@"+str(range_query_max_size)+"@0.0@0",
                   graph_title = "./no@3@10@3@10@ALL@"+str(set_size)+"@"+str(int(set_size/2))+"@"+str(reads)+"@"+str(range_queries)+"@"+str(range_query_max_size)+"@0.0@0",
                   legend = showLegend,
                   yaxis_max = yaxis_max,
                   append_to_column =5,
                   divide_by_number_of_milliseconds=False,
                   divide_by_number_of_queries=False)
        print("JoinsDIVmillseconds")
        draw_graph("./no@3@10@3@10@ALL@"+str(set_size)+"@"+str(int(set_size/2))+"@"+str(reads)+"@"+str(range_queries)+"@"+str(range_query_max_size)+"@0.0@0",
                   graph_title = "./no@3@10@3@10@ALL@"+str(set_size)+"@"+str(int(set_size/2))+"@"+str(reads)+"@"+str(range_queries)+"@"+str(range_query_max_size)+"@0.0@0",
                   legend = showLegend,
                   yaxis_max = yaxis_max,
                   append_to_column =4,
                   divide_by_number_of_milliseconds=True,
                   divide_by_number_of_queries=False)
        print("SplitsDIVmillseconds")
        draw_graph("./no@3@10@3@10@ALL@"+str(set_size)+"@"+str(int(set_size/2))+"@"+str(reads)+"@"+str(range_queries)+"@"+str(range_query_max_size)+"@0.0@0",
                   graph_title = "./no@3@10@3@10@ALL@"+str(set_size)+"@"+str(int(set_size/2))+"@"+str(reads)+"@"+str(range_queries)+"@"+str(range_query_max_size)+"@0.0@0",
                   legend = showLegend,
                   yaxis_max = yaxis_max,
                   append_to_column =5,
                   divide_by_number_of_milliseconds=True,
                   divide_by_number_of_queries=False)
        print("")
        print("")
        showLegend = True
plt.setp([a.get_xticklabels() for a in axarr[0, :]], visible=False)
plt.setp([a.get_yticklabels() for a in axarr[0, 1:]], visible=False) 

for ax in axarr[1, 0:]:
    ax.tick_params(axis="y",direction="in", pad=-16)

for ax in axarr[0, 0:]:
    ax.tick_params(axis="y",direction="in", pad=-26)

for ax in axarr[0, :]:
    ax.set_ylim(ymin=-40)

for ax in axarr[1, :-1]:
    ax.set_ylim(ymin=-0.5)

axarr[1, 4].set_ylim(ymin=-0.014)

axarr[0, 0].set_title("initial to X-1000")
axarr[0, 1].set_title("X-10")
axarr[0, 2].set_title("X-1000")
axarr[0, 3].set_title("X-10")
axarr[0, 4].set_title("X-100000")


f.subplots_adjust(hspace=.045,wspace=.030)

f.text(0.5, 0.045, 'Time (seconds)', ha='center', fontsize=9)

plt.savefig('combinedtimeseries.pdf', bbox_inches='tight', pad_inches = 0)
