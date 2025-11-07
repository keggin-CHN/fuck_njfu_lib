FROM python:3.9-slim
WORKDIR /app
COPY backend/ /app/
RUN pip install --no-cache-dir -r requirements.txt
RUN python init_db.py
EXPOSE 5000
CMD [\"gunicorn\", \"--workers\", \"3\", \"--worker-class\", \"gevent\", \"--bind\", \"0.0.0.0:5000\", \"app:app\"]
