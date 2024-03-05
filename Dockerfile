FROM gradle:8-jdk17-focal as builder
COPY . /tmp/benchmark-build

RUN cd /tmp/benchmark-build &&\
  ./gradlew clean deployTar

FROM amazoncorretto:17.0.10-al2023-headless as runner
COPY --from=builder /tmp/benchmark-build/build/distributions/benchmarks.tar /root/benchmarks.tar

ENV BENCHMARKS_PATH /opt/aeron-benchmarks

RUN dnf install -y tar iproute which bind-utils &&\
  mkdir -p ${BENCHMARKS_PATH} &&\
  tar -C ${BENCHMARKS_PATH} -xf /root/benchmarks.tar &&\
  rm -f /root/benchmarks.tar

ENTRYPOINT [ "/opt/aeron-benchmarks/scripts/k8s/k8s-benchmark-entrypoint.sh" ]
WORKDIR ${BENCHMARKS_PATH}/scripts
