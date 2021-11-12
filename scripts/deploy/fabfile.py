from fabric import Connection
from invoke import task
import os

app_tar_file = '../../build/distributions/benchmarks.tar'

@task
def clean(c):
    c.run('rm -r *')

@task
def killall(c):
    c.run('pkill -f java')

@task
def setup(c):
    c.run('sudo apt install numactl ; sudo sysctl -w net.core.wmem_max=16777216 ; sudo sysctl -w net.core.rmem_max=16777216')
    c.run('sudo ip link set dev ens6 down')

@task
def upload_and_unpack(c, jdk_tar_file):
    jdk_tar_basename = os.path.basename(jdk_tar_file)
    app_tar_basename = os.path.basename(app_tar_file)
    if c.run('test -f {}'.format(jdk_tar_basename), warn=True).failed:
        c.put(jdk_tar_file, ".")
        c.run('mkdir -p java && tar xf {} -C java --strip-components=1'.format(jdk_tar_basename))
    c.put(app_tar_file)
    c.run("mkdir -p aeron_benchmarks && rm -rf 'aeron_benchmarks/*' && tar xf {} -C aeron_benchmarks".format(app_tar_basename))
    c.run("mkdir -p data")





