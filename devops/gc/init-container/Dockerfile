FROM alpine:3.19.1

WORKDIR /opt

COPY ./curl.sh ./

RUN apk --no-cache add curl

RUN chmod +x /opt/curl.sh
RUN addgroup -g 10001 -S nonroot \
  && adduser -h /opt -G nonroot -S -u 10001 nonroot
RUN chown -R 10001:10001 /opt

USER 10001:10001

CMD ["/bin/sh", "-c", "/opt/curl.sh"]
