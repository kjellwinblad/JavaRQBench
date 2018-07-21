import sys
import subprocess

MEASURMENT_TIMES_SECONDS=['0.005', '0.01', '0.055', '0.1', '0.25', '0.4', '0.7', '1.0', '1.4', '1.8', '2.2', '2.4']

THREAD_COUNT="30"
start_range_size=int(sys.argv[4])
end_range_size=int(sys.argv[5])
SETTINGS=[(0.55,0.25,end_range_size,0.0,0,start_range_size)]
post_fix = sys.argv[1]
data_structure = sys.argv[2]
PINNING="no"
MEASURMENT_TIME_SECONDS="TIME_SERIES"
NR_OF_WARM_UP_RUNS="35"
WARM_UP_TIME_SECONDS="2"
NR_OF_MEASURMENT_RUNS="10"
REPORT_TYPE='ALL'
set_size=sys.argv[3]

for settings in SETTINGS:
    static_settings = [NR_OF_WARM_UP_RUNS,
                WARM_UP_TIME_SECONDS,
                NR_OF_MEASURMENT_RUNS,
                MEASURMENT_TIME_SECONDS,
                REPORT_TYPE,
                set_size,
                str(int(int(set_size)/2))] + list(map(lambda x: str(x), settings))
    output_file_name = "time_series@" + PINNING + "@" + THREAD_COUNT + "@".join(static_settings) +"_"  + data_structure + "_" + post_fix
    result_output = open(output_file_name, 'w')
    for run_time in MEASURMENT_TIMES_SECONDS:
        settings = list(map(lambda x: x.replace(MEASURMENT_TIME_SECONDS, run_time), static_settings))
        if int(settings[len(settings) -1]) == 0:
            settings = settings[:-1]
        command = ['java',
                   '-Xmx8g',
                   '-Xms8g',
                   '-XX:+UseCondCardMark', # To avoid false sharing (see https://blogs.oracle.com/dave/false-sharing-induced-by-card-table-marking)
                   '-server',
                   '-d64',
                   '-cp',
                   '../target/scala-2.11/test-classes/:../target/scala-2.11/classes/',
                   'se.uu.bench.IntSetRangeUpdateMixBench',
                   data_structure,
                   THREAD_COUNT]+ settings
        if PINNING=='no':
            command=command
        else:
            thread_count = int(THREAD_COUNT)
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
        print(command)
        print("running command: " + " ".join(command))
        output = subprocess.check_output(command).decode("utf-8")
        result_output.write(str(run_time) + " " + str(output))
    result_output.close()
