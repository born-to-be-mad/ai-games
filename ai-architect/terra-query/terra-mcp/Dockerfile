FROM python:3.12-slim

WORKDIR /app

# Install runtime dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy application code
COPY . .

# Data and index directories (mounted as volumes in production)
RUN mkdir -p /data/indices

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD python -c "import socket; s=socket.socket(); s.settimeout(5); s.connect(('127.0.0.1',8080)); s.close()" || exit 1

ENV MCP_TRANSPORT=streamable-http
ENV PYTHONUNBUFFERED=1

CMD ["python", "server.py"]
