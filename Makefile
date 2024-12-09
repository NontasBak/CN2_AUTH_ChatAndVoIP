# To run both the backend and frontend: make
# To stop both: make stop
all: build run-backend run-frontend

build:
	mvn compile
	cd frontend && npm install

run-backend:
	mvn spring-boot:run &

run-frontend:
	cd frontend && npm run dev &
    
# This is a workaround to kill the frontend and backend processes
# Ignore the "BUILD FAILURE" error.
stop:
	@echo "Stopping backend and frontend..."
	cd frontend && npm run kill-ports 
	@echo "Ports killed."

run-normal-app:
	java -Dsun.java2d.uiScale=3 -cp target/classes com.cn2.communication.App

clean:
	mvn clean

