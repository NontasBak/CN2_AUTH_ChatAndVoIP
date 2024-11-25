# To compile the project: make build
# To run it: make run
# To do both: make

JAVA = java
MAIN_CLASS = com.cn2.communication.App
OUT_DIR = target/classes
SCALE = 3 # Lower this if the GUI is too big

all: build run

build:
	mvn compile

run:
	$(JAVA) -Dsun.java2d.uiScale=$(SCALE) -cp $(OUT_DIR) $(MAIN_CLASS)

clean:
	mvn clean
