FROM gradle:8-jdk17-focal as builder
COPY . /tmp/benchmark-build
WORKDIR /tmp/benchmark-build

RUN --mount=type=cache,target=/root/.gradle \
  --mount=type=cache,target=/home/gradle/.gradle \
  --mount=type=cache,target=/tmp/benchmark-build/.gradle \
  ./gradlew --no-daemon -i clean deployTar

FROM azul/zulu-openjdk:17-latest as runner
COPY --from=builder /tmp/benchmark-build/build/distributions/benchmarks.tar /root/benchmarks.tar

ENV BENCHMARKS_PATH /opt/aeron-benchmarks

RUN apt-get update &&\
  apt-get install -y \
  tar \
  gzip \
  iproute2 \
  bind9-utils \
  bind9-host \
  jq \
  lsb-release \
  hwloc &&\
  mkdir -p ${BENCHMARKS_PATH} &&\
  tar -C ${BENCHMARKS_PATH} -xf /root/benchmarks.tar &&\
  rm -f /root/benchmarks.tar

ENTRYPOINT [ "/opt/aeron-benchmarks/scripts/k8s/k8s-benchmark-entrypoint.sh" ]
WORKDIR ${BENCHMARKS_PATH}/scripts
