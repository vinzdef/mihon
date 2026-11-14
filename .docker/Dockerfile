FROM eclipse-temurin:21-jdk

RUN apt-get update && apt-get install -y --no-install-recommends git unzip wget \
    && rm -rf /var/lib/apt/lists/* \
    && git config --system --add safe.directory '*'

ENV ANDROID_HOME=/opt/android-sdk
ENV PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools
RUN mkdir -p $ANDROID_HOME/cmdline-tools && cd /tmp \
    && wget -q https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip -O clt.zip \
    && unzip -q clt.zip && mv cmdline-tools $ANDROID_HOME/cmdline-tools/latest && rm clt.zip \
    && yes | sdkmanager --licenses >/dev/null \
    && sdkmanager "platform-tools" "platforms;android-37.2" "build-tools;36.0.0" >/dev/null

WORKDIR /src
