FROM python:3.12-slim

# Prevent Python from writing .pyc files and enable unbuffered logging
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PORT=10000

# Install OpenJDK 17 JDK and Maven
RUN apt-get update && \
    apt-get install -y --no-install-recommends openjdk-17-jdk maven && \
    rm -rf /var/lib/apt/lists/*

# Set working directory to /app
WORKDIR /app

# Copy requirements.txt and install Python dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy the entire project into /app
COPY . .

# Run mvn -B clean package to build the Java application
RUN mvn -B clean package

# Expose port 10000
EXPOSE 10000

# Start the Flask dashboard using python web_dashboard.py
CMD ["python", "web_dashboard.py"]
