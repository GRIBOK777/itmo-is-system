.PHONY: all clean test run fmt fmt-check hooks

BIN_DIR = bin
SRC_DIR = src
TEST_DIR = test
OUT_DIR = out
LIB_DIR = lib

CLASSPATH = $(LIB_DIR)/*

JAVA        = java
JAVAC       = javac
JAVAP       = javap

JAVACFLAGS = -d $(OUT_DIR) -cp "$(CLASSPATH):$(OUT_DIR)" --source-path $(SRC_DIR) --release 25 --enable-preview -Xlint:preview
JVMFLAGS = -XX:+UseSerialGC -Xmx1500m -Xms1500m
JAVAFLAGS_SRC = -cp "$(CLASSPATH):$(OUT_DIR)" --enable-preview $(JVMFLAGS) --enable-native-access=ALL-UNNAMED
JAVAFLAGS_TEST = -cp "$(CLASSPATH):$(OUT_DIR)" -ea --enable-preview --enable-native-access=ALL-UNNAMED
JAVAPFLAGS = -p -s -l -v -c
DEBUG ?= 0
ifeq ($(DEBUG), 1)
	JAVACFLAGS += -verbose
	JAVACFLAGS += -g
	JAVAFLAGS_TEST += -verbose
	JAVAFLAGS_SRC += -verbose
endif

FILES_SRC = $(shell find $(SRC_DIR) -name "*.java")
FILES_TEST = $(shell find $(TEST_DIR) -name "*.java")
GJF_JAR = $(shell find $(LIB_DIR) -name "google-java-format-*-all-deps.jar" -print -quit)

MAINCLASS_SRC = org.gribok777.lab.Main
MAINCLASS_TEST = org.gribok777.lab.MainTest


all: build

BUILD_SENTINEL = $(OUT_DIR)/.build_sentinel
build: deps $(BUILD_SENTINEL)

$(BUILD_SENTINEL): $(FILES_SRC) $(FILES_TEST) Makefile
	mkdir -p $(OUT_DIR)
	$(JAVAC) $(JAVACFLAGS) $(FILES_SRC)
	$(JAVAC) $(JAVACFLAGS) $(FILES_TEST)
	touch $(BUILD_SENTINEL)

test: build
	$(JAVA) $(JAVAFLAGS_TEST) $(MAINCLASS_TEST)

run: build
	$(JAVA) $(JAVAFLAGS_SRC) $(MAINCLASS_SRC)

clean:
	rm -rf $(OUT_DIR)
	rm -rf $(LIB_DIR)
	rm -rf $(BIN_DIR)

DEPS_SENTINEL = $(LIB_DIR)/.deps_sentinel
deps: $(DEPS_SENTINEL)

$(DEPS_SENTINEL): scripts/deps.bash
	mkdir -p $(LIB_DIR)
	bash scripts/deps.bash $(LIB_DIR)
	touch $(DEPS_SENTINEL)

$(OUT_DIR)/%.class: build
	$(JAVAP) $(JAVAPFLAGS) -cp "$(CLASSPATH)" $@

fmt: deps
	$(JAVA) -jar $(GJF_JAR) --aosp --replace $(FILES_SRC) $(FILES_TEST)

hooks:
	git config core.hooksPath .githooks
