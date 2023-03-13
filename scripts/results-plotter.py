#!/usr/bin/env python3

"""
Script for plotting aggregated benchmark results, grouping them by scenario.
Generated png files are stored in the source directory.

usage: results-plotter.py [-h] [--group-by GROUP_BY] [--filter FILTER] [--percentiles-range-max PERCENTILES_RANGE_MAX] directory

The script expects files to be in the format

    <type>_<scenario>_ctx_<context parameters>_params_<experiment parameters>_sha-<sha1>-report.hgrm

e.g.

    echo_java_ctx_instance-c5n.9xlarge_window-2m_params_mtu-8896_sndbuf-2m_rcvbuf-2m_rcvwnd-2m_msgrate-100000_burstsize-1_msgsize-1344_sha-c68b27d86b43946be1a4a1aaebcfa69c4506cb37af013ced29daccf39412d3c3-report.hgrm
"""

import argparse
import os
import tempfile
import shutil
import re
import sys
from collections import defaultdict

# <type>_<scenario>_ctx_[c1-v1_c2-v2_...]_params_[p1-v1_p2-v2_...]_sha-<sha1>-report.hgrm
regex_common = re.compile('(?P<type>[a-z]+)_(?P<scenario>[a-z0-9-]+)_ctx_(?P<context>([a-z]+-[0-9a-zA-Z-\.]+_)+)params_(?P<params>([a-z]+-[0-9a-zA-Z-\.]+_)+)sha-(?:[a-z0-9]+)-report.hgrm')
regex_params = re.compile('([a-z]+)-([0-9a-zA-Z\.-]+)')


# TODO feature to overwrite the graph title
def main():
    parser = argparse.ArgumentParser(description='Plot benchmark results.')
    parser.add_argument('directory', help='path of the directory containing the aggregated results.')
    parser.add_argument('--group-by', default='scenario', help='comma-separated list of fields by which to group the results on a graph. Example: instance,msgsize')
    parser.add_argument('--filter', help='comma-separated list of fields to filter for (include). multiple values should be repeated, Example: msgsize=32,msgsize=288,scenario=c-ats')
    parser.add_argument('--exclude', help='comma-separated list of fields to filter for (exclude). for. multiple values should be repeated, Example: msgsize=1344,scenario=java')
    parser.add_argument('--percentiles-range-max', default='99.9999', help='maximum percentiles to display. Example: 99.999')
    args = parser.parse_args(args=None if sys.argv[1:] else ['--help'])

    group_by = args.group_by.strip().split(',')
    filters = parse_filter(args.filter)
    excludes = parse_filter(args.exclude)

    path = os.path.abspath(args.directory)

    if not os.path.exists(path):
        sys.exit('Directory ' + args.directory + ' does not exist.')

    if has_processable_files(path):
        plot_graphs(path, args.percentiles_range_max, regex_common, group_by, filters, excludes)
    else:
        sys.exit("No files in the correct format found, expected files with names like <type>_<scenario>_ctx_[c1-v1_c2-v2_...]_params_[p1-v1_p2-v2_...]_sha-<sha1>-report.hgrm")


def plot_graphs(path, percentiles_range_max, regex, group_by, filters, excludes):
    """Plots the graphs by invoking hdr-plot"""

    grouped = defaultdict(list)
    files = list((parse_file_name(file) for file in os.scandir(path) if re.match(regex, file.name)))

    accepted_files = filter_files(filter_files(files, filters), excludes, exclude=True)

    for f in accepted_files:
        keys = get_key_fields(f, group_by)
        key = tuple(keys .items())
        grouped[key].append(f)

    for key in grouped.keys():
        with tempfile.TemporaryDirectory() as tmpdir:
            grouped_files = []
            for f in grouped[key]:
                field_values = []
                for field in group_by:
                    # rename this for hdr-plot
                    valid_field_value = f.fields[field].replace('.', '-')
                    field_values.append(valid_field_value)
                grouped_files.append('_'.join(field_values) + '.hgrm')
                shutil.copyfile(f.file.path, os.path.join(tmpdir, '_'.join(field_values) + '.' + 'hgrm'))

            histogram_files = ' '.join(sorted(grouped_files, reverse=True))

            filename, title = get_plot_filename_and_title(key)
            os.chdir(tmpdir)
            os.system(f'hdr-plot --percentiles-range-max={percentiles_range_max} --output {filename} --title "{title}" {histogram_files}')

            shutil.copyfile(os.path.join(tmpdir, filename), os.path.join(path, filename))


def has_processable_files(path):
    """Checks if the path has any files that can be processed by the script."""

    files = (file for file in os.scandir(path) if re.match(regex_common, file.name))
    return list(files)


def filter_files(files, filters, exclude=False):
    """Filters the list of files based on the provided filters (inclusive by default, or exclusive)"""

    accepted_files = set()
    for f in files:
        for filter_field in filters:
            field_value = f.fields[filter_field]
            if field_value in filters[filter_field]:
                if not exclude:
                    accepted_files.add(f)
            elif exclude:
                accepted_files.add(f)

    if not filters:
        accepted_files = files

    return accepted_files


def get_key_fields(benchmark_file, group_by):
    """Returns the fields that should be used as the key to group files by."""

    key = dict(benchmark_file.fields)

    # remove fields we want to group by
    for k in group_by:
        key.pop(k)

    return key


def parse_file_name(file):
    """Returns a BenchmarkFile representation of the file which contains the parsed fields in a useful representation."""

    match = re.search(regex_common, file.name)

    # given the regex, the last context and parameters will end in _ which we need to remove
    ctx_str = match.group('context')[:-1]
    params_str = match.group('params')[:-1]

    params = dict()
    ctx = dict()
    for k, v in re.findall(regex_params, params_str):
        params[k] = v
    for k, v in re.findall(regex_params, ctx_str):
        ctx[k] = v

    return BenchmarkFile(file=file, type=match.group('type'), scenario=match.group('scenario'), ctx=ctx, params=params, ctx_raw=ctx_str, params_raw=params_str)


def parse_filter(filter_str):
    """Parses a filter command-line argument, expecting the format field1=value1,field1=value2,field2=value3"""

    filters = defaultdict(set)

    if not filter_str:
        return filters

    filter_args = filter_str.strip().split(',')
    for f in filter_args:
        param, value = f.split('=')
        filters[param].add(value)

    return filters


def get_plot_filename_and_title(key):
    """Builds a default title and filename for the graph, using the available parameters."""
    fields = dict(key)
    type = fields.pop('type')

    # we're only left with params by now, generate a title from all parameters
    params_title = []
    params_filename = []
    for k, v in fields.items():
        params_title.append(f'{k} {v}')
        params_filename.append(f'{k}-{v}')
    params_title_str = ', '.join(params_title)
    params_filename_str = '_'.join(params_filename)
    print(params_filename_str)
    title = f'{type} {params_title_str}'
    filename = f'{type}_{params_filename_str}.png'

    return filename, title


class BenchmarkFile:
    def __init__(self, file, type, scenario, ctx, params, ctx_raw, params_raw):
        self.file = file
        self.type = type
        self.scenario = scenario
        self.ctx = ctx
        self.params = params
        self.ctx_raw = ctx_raw
        self.params_raw = params_raw

        self.fields = self.to_keys_dict()

    def to_keys_dict(self):
        keys = {
            "type": self.type,
            "scenario": self.scenario
        }
        keys.update(self.ctx)
        keys.update(self.params)
        return keys


if __name__ == "__main__":
    main()