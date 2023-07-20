# gnuplot -p -e "filename='foo.csv'" latency_around_failover.p

set title "latency around failover"
set xlabel "request time (seconds)"
set ylabel "response latency (microseconds)"
set logscale y 10
set xtics 1
set datafile separator ','
first(x) = ($0 > 0 ? base : base = x)
plot filename every ::1 using (($1-first($1))/1000000000):(($2-$1)/1000) notitle with dots
