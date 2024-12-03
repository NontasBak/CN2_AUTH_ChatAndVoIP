# To run both the backend and frontend: make
# To stop both: make stop
JAVA = java
MAIN_CLASS = com.cn2.communication.App
OUT_DIR = target/classes
SCALE = 3 # Lower this if the GUI is too big

all: build run

build:
	mvn compile
	cd frontend && npm install

run: run-backend run-frontend
	@echo "Both frontend and backend are running..."

run-backend:
	mvn spring-boot:run & echo $$! > backend.pid

run-frontend:
	cd frontend && npm run dev & echo $$! > frontend.pid
    
stop:
	@echo "Stopping backend and frontend..."
	@-pkill -F backend.pid 2>/dev/null || true
	@-pkill -F frontend.pid 2>/dev/null || true
	@rm -f backend.pid frontend.pid

clean:
	mvn clean
	cd frontend && npm run clean