import sys
import subprocess
THREAD_COUNTS=[32] #,62
SETTINGS=[[2],[4],[8],[32],[128],[512], [2000], [8000],[32000],[128000]]
post_fix = sys.argv[1]
data_structure = sys.argv[2]
PINNING="no"

NR_OF_WARM_UP_RUNS="3"
WARM_UP_TIME_SECONDS="10"
NR_OF_MEASURMENT_RUNS="3"
MEASURMENT_TIME_SECONDS="10"
REPORT_TYPE='ALL'#AVERAGE_THROUGHPUT
set_size=sys.argv[3]

#se.uu.collection.ImmTreapCATreeMapSTDR 63 1 10 5 100 ALL 1000000 500000  0.8 0.1 1 0 0 0

for thread_count in THREAD_COUNTS:
    output_file_name = PINNING + "@threads" + str(thread_count)+ "_" + set_size +"_"  + data_structure + "_" + post_fix
    result_output = open(output_file_name, 'w')
    for settings in SETTINGS:
        range_size = settings[0]
        settings = [NR_OF_WARM_UP_RUNS,
                    WARM_UP_TIME_SECONDS,
                    NR_OF_MEASURMENT_RUNS,
                    MEASURMENT_TIME_SECONDS,
                    REPORT_TYPE,
                    set_size,
                    str(int(int(set_size)/2))] + list(map(lambda x: str(x), settings))
        command = ['java',
                   '-Xmx8g',
                   '-Xms8g',
                   '-XX:+UseCondCardMark', # To avoid false sharing (see https://blogs.oracle.com/dave/false-sharing-induced-by-card-table-marking)
                   '-server',
                   '-d64',
                   '-cp',
                   '../target/scala-2.11/test-classes/:../target/scala-2.11/classes/',
                   'se.uu.bench.IntSetRangeQueryAndUpdate',
                   data_structure,
                   str(thread_count)]+ settings
        if PINNING=='no':
            command=command
        else:
            thread_count = int(thread_count)
            nomactrl = []
            if thread_count > 48:
                nomactrl = ['numactl', '--cpunodebind=0,1,2,3']#--membind=nodes
            elif thread_count > 32:
                nomactrl = ['numactl', '--cpunodebind=0,1,2']
            elif thread_count > 16:
                nomactrl = ['numactl', '--cpunodebind=0,1']
            else:
                nomactrl = ['numactl', '--cpunodebind=0']
            command = nomactrl + command
        print("running command: " + " ".join(command))
        output = subprocess.check_output(command).decode("utf-8")
        result_output.write(str(range_size) + " " + str(output))
    result_output.close()
