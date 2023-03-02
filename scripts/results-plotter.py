#!/usr/bin/env python3

"""
Script for plotting aggregated benchmark results, grouping them by scenario.
Generated png files are stored in the source directory.

usage: results-plotter.py [-h] [--type TYPE] [--percentiles-range-max PERCENTILES_RANGE_MAX] directory

The script expects files to be in the format

    <type>_<scenario>_<optional parameters for the scenario>_<msg rate>_<burst size>_<msg size>_<sha1>-report.hgrm

e.g.

    cluster_java-onload_fsync-0-mtu-1408_50000_10_224_a3a5e4dbc707a50dd508bdcc5416e0e580596cf14aeb540dec31b752895623e8-report.hgrm

Legacy formats are supported for echo, cluster and archive (live replay and live recording).
"""

import argparse
import os
import tempfile
import shutil
import re
import sys
from collections import defaultdict

# <type>_<scenario>_[p1-v1-p2-v2-...]_<msg rate>_<burst size>_<msg size>_<sha1>-report.hgrm
regex_common = re.compile('(?P<type>[a-z]+)_(?P<scenario>[a-z0-9-]+)_(?P<params>([a-z]+-[0-9]+-?)*)_(?P<msg_rate>[0-9]+)_(?P<burst_size>[0-9]+)_(?P<msg_size>[0-9]+)_(?P<sha>[a-z0-9]+)-report.hgrm')
regex_params = re.compile('([a-z]+)-([0-9]+)')

# echo-<scenario>-mtu-<mtu>_<msg rate>_<burst size>_<msg size>_<sha1>
regex_echo = re.compile('echo-(?P<scenario>[a-z-]+)-(?:mtu-)([0-9]+)_([0-9]+)_([0-9]+)_([0-9]+)_([a-z0-9]+)-report.hgrm')

# cluster-<scenario>-fsync-<fsync>[-mtu-<mtu>]_<msg rate>_<burst size>_<msg size>_<sha1>
regex_cluster = re.compile('cluster-(?P<scenario>[a-z-_]+)-(?:fsync-)([0-9]+)-?(?:mtu-)?([0-9]*)_([0-9]+)_([0-9]+)_([0-9]+)_([a-z0-9]+)-report.hgrm')

# <scenario>-fsync-<fsync>[-mtu-<mtu>]_<msg rate>_<burst size>_<msg size>_<sha1>
regex_archive = re.compile('(live-replay|live-recording)-(?P<scenario>[a-z-_]+)-(?:fsync-)([0-9]+)-?(?:mtu-)?([0-9]*)_([0-9]+)_([0-9]+)_([0-9]+)_([a-z0-9]+)-report.hgrm')


def main():
    parser = argparse.ArgumentParser(description='Plot benchmark results.')
    parser.add_argument('directory', help='path of the directory containing the aggregated results.')
    parser.add_argument('--type', help='type of the benchmark, required for legacy benchmark results. can be: echo, cluster, archive')
    parser.add_argument('--percentiles-range-max', default='99.9999', help='maximum percentiles to display, e.g. 99.999')
    args = parser.parse_args(args=None if sys.argv[1:] else ['--help'])

    path = os.path.abspath(args.directory)

    if not os.path.exists(path):
        sys.exit('Directory ' + args.directory + ' does not exist.')

    if not is_legacy_result(path):
        plot_graphs(path, args.percentiles_range_max, regex_common, get_key_components, get_plot_filename_and_title)
    else:
        if not args.type:
            sys.exit("Legacy benchmark results require the --type argument to be provided")
        match args.type:
            case 'echo':
                plot_graphs(path, args.percentiles_range_max, regex_echo, get_echo_key_components, get_echo_plot_filename_and_title)
            case 'cluster':
                plot_graphs(path, args.percentiles_range_max, regex_cluster, get_cluster_key_components, get_cluster_plot_filename_and_title)
            case 'archive':
                plot_graphs(path, args.percentiles_range_max, regex_archive, get_archive_key_components, get_archive_plot_filename_and_title)
            case other:
                sys.exit(f'Legacy benchmark type {other} not supported ')


def plot_graphs(path, percentiles_range_max, regex, key_getter, filename_and_title_getter):
    grouped = defaultdict(list)
    files = (file for file in os.scandir(path) if re.match(regex, file.name))
    for f in files:
        key = tuple(sorted(key_getter(f.name) .items()))
        grouped[key].append(f)

    for key in grouped.keys():
        with tempfile.TemporaryDirectory() as tmpdir:
            scenario_files = []
            for f in grouped[key]:
                scenario = re.search(regex, f.name).group('scenario')
                scenario_files.append(scenario + '.hgrm')
                shutil.copyfile(f.path, os.path.join(tmpdir, scenario + '.' + 'hgrm'))

            histogram_files = ' '.join(sorted(scenario_files, reverse=True))
            filename, title = filename_and_title_getter(key)
            os.chdir(tmpdir)
            os.system(f'hdr-plot --percentiles-range-max={percentiles_range_max} --output {filename} --title "{title}" {histogram_files}')

            shutil.copyfile(os.path.join(tmpdir, filename), os.path.join(path, filename))


def is_legacy_result(path):
    files = (file for file in os.scandir(path) if re.match(regex_common, file.name))
    return not list(files)


def get_key_components(file_name):
    match = regex_common.search(file_name)

    # extract optional scenario parameters
    params = dict()
    for k, v in re.findall(regex_params, match.group('params')):
        params[k] = v

    components = dict(match.groupdict())
    components.update(params)
    return components


def get_plot_filename_and_title(key_dict):
    components = dict(key_dict)
    type = components.pop('type')

    rate = components.pop('msg_rate')
    burst_size = components.pop('burst_size')
    msg_size = components.pop('msg_size')

    # we're only left with params by now
    params_filename = []
    params_title = []
    for k, v in components.items():
        params_filename.append(f'{k}-{v}')
    params_filename_str = params_title.join('-')
    params_title_str = params_filename.join(' ')

    filename = f'{type}_{params_filename_str}_{rate}_{burst_size}_{msg_size}.png'
    title = f'{type} {msg_size}@{rate} burst size {burst_size} {params_title_str}'

    return filename, title


def get_echo_key_components(file_name):
    match = re.findall(regex_echo, file_name)
    return {
        'type': 'echo',
        'mtu': match[0][1],
        'rate': match[0][2],
        'burst_size': match[0][3],
        'msg_size': match[0][4]
    }


def get_cluster_key_components(file_name):
    match = re.findall(regex_cluster, file_name)
    return {
        'type': 'cluster',
        'fsync': match[0][1],
        'mtu': match[0][2],
        'rate': match[0][3],
        'burst_size': match[0][4],
        'msg_size': match[0][5]
    }


def get_archive_key_components(file_name):
    match = re.findall(regex_cluster, file_name)
    return {
        'type': 'archive',
        'fsync': match[0][1],
        'mtu': match[0][2],
        'rate': match[0][3],
        'burst_size': match[0][4],
        'msg_size': match[0][5]
    }


def get_echo_plot_filename_and_title(key_dict):
    components = dict(key_dict)
    mtu = components['mtu']
    rate = components['rate']
    burst_size = components['burst_size']
    msg_size = components['msg_size']

    filename = f'echo-mtu-{mtu}_{rate}_{burst_size}_{msg_size}.png'
    title = f'Echo {msg_size}@{rate}, burst size {burst_size}, MTU {mtu}'

    return filename, title


def get_cluster_plot_filename_and_title(key_dict):
    components = dict(key_dict)
    fsync = components['fsync']
    mtu = components['mtu']
    rate = components['rate']
    burst_size = components['burst_size']
    msg_size = components['msg_size']

    if mtu != '':
        filename = f'cluster_fsync-{fsync}-mtu-{mtu}_{rate}_{burst_size}_{msg_size}.png'
        title = f'Cluster {msg_size}@{rate}, fsync {fsync}, burst size {burst_size}, MTU {mtu}'
    else:
        filename = f'cluster_fsync-{fsync}_{rate}_{burst_size}_{msg_size}.png'
        title = f'Cluster {msg_size}@{rate}, fsync {fsync}, burst size {burst_size}'

    return filename, title


def get_archive_plot_filename_and_title(key_dict):
    components = dict(key_dict)
    fsync = components['fsync']
    mtu = components['mtu']
    rate = components['rate']
    burst_size = components['burst_size']
    msg_size = components['msg_size']

    if mtu != '':
        filename = f'archive_fsync-{fsync}-mtu-{mtu}_{rate}_{burst_size}_{msg_size}.png'
        title = f'Archive {msg_size}@{rate}, fsync {fsync}, burst size {burst_size}, MTU {mtu}'
    else:
        filename = f'archive_fsync-{fsync}_{rate}_{burst_size}_{msg_size}.png'
        title = f'Archive {msg_size}@{rate}, fsync {fsync}, burst size {burst_size}'

    return filename, title


if __name__ == "__main__":
    main()