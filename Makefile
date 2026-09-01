.PHONY: all clean test run

BIN_DIR = bin
SRC_DIR = src
TEST_DIR = test
OUT_DIR = out
TEST_OUT = $(OUT_DIR)/test
LIB_DIR = lib

CLASSPATH = $(LIB_DIR)/*

JAVA        = java
JAVAC       = javac
JAVAP       = javap

JAVACFLAGS = --release 25
JVMFLAGS = -XX:+UseSerialGC -Xmx1500m -Xms1500m
NATIVE_ACCESS_MODULES = org.gribok777.lab.database
JAVAFLAGS_SRC = --module-path $(OUT_DIR) --enable-preview $(JVMFLAGS) --enable-native-access=$(NATIVE_ACCESS_MODULES)
JAVAFLAGS_TEST = --module-path $(OUT_DIR) --class-path "$(TEST_OUT):$(CLASSPATH)" --add-modules org.gribok777.lab.logger,$(NATIVE_ACCESS_MODULES) -ea --enable-preview --enable-native-access=$(NATIVE_ACCESS_MODULES)
JAVAPFLAGS = -p -s -l -v -c
DEBUG ?= 0
ifeq ($(DEBUG), 1)
	JAVACFLAGS += -verbose
	JAVACFLAGS += -g
	JAVAFLAGS_TEST += -verbose
	JAVAFLAGS_SRC += -verbose
endif

FILES_SRC = $(shell find $(SRC_DIR) -name "*.java")
MODULE_DESCRIPTORS = $(shell find $(SRC_DIR) -name "module-info.java")
FILES_TEST = $(shell find $(TEST_DIR) -name "*.java")

MAINCLASS_SRC = org.gribok777.lab.launcher.Launcher
MAINCLASS_TEST = org.gribok777.lab.test.MainTest


all: build

BUILD_SENTINEL = $(OUT_DIR)/.build_sentinel
build: deps $(BUILD_SENTINEL)

$(BUILD_SENTINEL): $(FILES_SRC) $(FILES_TEST) Makefile
	mkdir -p $(OUT_DIR) $(TEST_OUT)
	$(JAVAC) $(JAVACFLAGS) --module-source-path "$(SRC_DIR)/*" -d $(OUT_DIR) $(FILES_SRC)
	$(JAVAC) $(JAVACFLAGS) -d $(TEST_OUT) --module-path $(OUT_DIR) --add-modules org.gribok777.lab.logger $(FILES_TEST)
	touch $(BUILD_SENTINEL)

test: build
	$(JAVA) $(JAVAFLAGS_TEST) $(MAINCLASS_TEST)

run: build
	$(JAVA) $(JAVAFLAGS_SRC) -m org.gribok777.lab.launcher/$(MAINCLASS_SRC)

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
