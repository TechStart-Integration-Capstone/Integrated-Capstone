FROM eclipse-temurin:17-jre-alpine

ARG JMETER_VERSION=5.6.3
ENV JMETER_HOME=/opt/apache-jmeter-${JMETER_VERSION}
ENV PATH=${JMETER_HOME}/bin:${PATH}

COPY apache-jmeter.tgz /tmp/jmeter.tgz
RUN tar -xzf /tmp/jmeter.tgz -C /opt && rm -f /tmp/jmeter.tgz

WORKDIR /jmeter

ENTRYPOINT ["jmeter"]
CMD ["-h"]
