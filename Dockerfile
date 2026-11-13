FROM eclipse-temurin:17-jdk-jammy

ENV PYTHONUNBUFFERED=1
ENV PORT=10000

RUN apt-get update && \
    apt-get install -y --no-install-recommends python3 python3-pip maven && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY requirements.txt .
RUN pip3 install --no-cache-dir -r requirements.txt

COPY . .

RUN mvn -B clean package

EXPOSE 10000

CMD ["python3", "web_dashboard.py"]
