# Experimental ARM64 Linux userspace for Storm Runtime.
# Built as a container image, then exported as a plain root filesystem tarball.
FROM --platform=linux/arm64 debian:bookworm-slim

ENV DEBIAN_FRONTEND=noninteractive
ENV LANG=C.UTF-8
ENV LC_ALL=C.UTF-8

RUN apt-get update \
 && apt-get install -y --no-install-recommends \
      bash ca-certificates locales \
      coreutils dash dpkg apt findutils grep sed gawk \
      tar gzip xz-utils procps file \
      libgcc-s1 libstdc++6 zlib1g libzstd1 libunwind8 \
      libx11-6 libxext6 libxrender1 libxrandr2 libxi6 \
      libxcursor1 libxinerama1 libasound2 libpulse0 \
 && apt-get clean \
 && rm -rf /var/lib/apt/lists/* /var/cache/apt/archives/* \
 && mkdir -p /dev /proc /sys /tmp /run /root /home/storm \
 && chmod 1777 /tmp \
 && : > /etc/machine-id \
 && echo 'Storm Runtime ARM64 rootfs' > /etc/hostname

CMD ["/bin/bash"]
