SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR ?= $(HOME)/ti/simplelink_lowpower_f3_sdk_9_20_01_21

include $(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)/imports.mak

CC = "$(TICLANG_ARMCOMPILER)/bin/tiarmclang"
LNK = "$(TICLANG_ARMCOMPILER)/bin/tiarmclang"

SYSCONFIG_GUI_TOOL = $(dir $(SYSCONFIG_TOOL))sysconfig_gui$(suffix $(SYSCONFIG_TOOL))
SYSCFG_CMD_STUB = $(SYSCONFIG_TOOL) --compiler ticlang --product $(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)/.metadata/product.json
SYSCFG_GUI_CMD_STUB = $(SYSCONFIG_GUI_TOOL) --compiler ticlang --product $(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)/.metadata/product.json
SYSCFG_FILES := $(shell $(SYSCFG_CMD_STUB) --listGeneratedFiles --listReferencedFiles --output . ../../freertos/soil_sensor.syscfg)

SYSCFG_C_FILES = $(filter %.c,$(SYSCFG_FILES))
SYSCFG_H_FILES = $(filter %.h,$(SYSCFG_FILES))
SYSCFG_OPT_FILES = $(filter %.opt,$(SYSCFG_FILES))

# Enable verbose output by setting VERBOSE=1
V := @
ifeq ($(VERBOSE), 1)
  V :=
endif

OBJECTS = app_app_main.obj app_app_menu.obj app_Profiles_app_dev_info.obj common_Services_dev_info_dev_info_service.obj common_BLEAppUtil_bleapputil_task.obj common_BLEAppUtil_bleapputil_init.obj common_BLEAppUtil_bleapputil_process.obj common_BLEAppUtil_bleapputil_stack_callbacks.obj common_BLEAppUtil_bleapputil_timers.obj common_Startup_osal_icall_ble.obj common_Startup_rom_init.obj common_lib_opt_ctrl_opt_rssi_monitor.obj common_lib_opt_ctrl_opt_connection_handover.obj common_lib_opt_ctrl_opt_dmm.obj common_lib_opt_ctrl_opt_dmm_dynamic_priority.obj common_lib_opt_ctrl_opt_ts_periodic.obj common_lib_opt_ctrl_opt_cm.obj common_lib_opt_ctrl_opt_health_check.obj common_lib_opt_ctrl_opt_ble_health.obj common_lib_opt_ctrl_opt_padv.obj common_lib_opt_ctrl_opt_pscan.obj common_lib_opt_ctrl_opt_pawr_scan.obj common_lib_opt_ctrl_opt_pawr_advertiser.obj common_lib_opt_ctrl_opt_cs.obj common_lib_opt_ctrl_opt_cs_test.obj common_lib_opt_ctrl_opt_initiator.obj common_lib_opt_ctrl_opt_connectable.obj common_lib_opt_ctrl_opt_scanner.obj common_lib_opt_ctrl_opt_power_control.obj common_lib_opt_ctrl_opt_adv_conn.obj common_lib_opt_ctrl_opt_adv_nconn.obj common_lib_opt_ctrl_opt_ae.obj common_lib_opt_ctrl_opt_adaptivity.obj common_lib_opt_ctrl_opt_past_sender.obj common_lib_opt_ctrl_opt_past_receiver.obj common_lib_opt_ctrl_opt_vendor_specific_cmd.obj common_lib_opt_ctrl_opt_ext_vendor_specific_cmd.obj common_lib_opt_ctrl_opt_coex.obj common_lib_opt_ctrl_opt_legacy_cmd.obj common_lib_opt_host_opt_gap_bond_mgr.obj common_lib_opt_host_opt_gap_pawr_scan.obj common_lib_opt_host_opt_gap_pawr_adv.obj common_lib_opt_host_opt_gap_past_receiver.obj common_lib_opt_host_opt_gap_past_sender.obj common_Drivers_NV_crc.obj common_Drivers_NV_nvocmp.obj common_iCall_icall.obj common_iCall_icall_user_config.obj common_config_ble_user_config.obj common_config_hci_supported_cmd.obj app_app_peripheral.obj app_app_connection.obj app_app_data.obj app_soil_sensor.obj app_battery_gauge.obj app_app_soil_sensor.obj app_soil_history.obj app_Profiles_soil_sensor_profile.obj app_Profiles_battery_service.obj app_Profiles_app_oad.obj common_Services_oad_oad_service.obj common_Services_oad_oad_reset_service.obj common_Profiles_oad_oad_profile.obj common_Profiles_oad_ms_oad_profile.obj common_Util_SwUpdate_sw_update.obj common_Util_SwUpdate_flash_interface_oad.obj common_Util_util.obj HealthToolkit_debugInfo.obj HealthToolkit_ble_sys_stat.obj common_MenuModule_menu_module.obj common_iCall_icall_POSIX.obj common_Startup_main_freertos.obj $(patsubst %.c,%.obj,$(notdir $(SYSCFG_C_FILES)))

NAME = soil_sensor


DFLAGS += -gdwarf-3

CFLAGS += -I../.. \
    -I. \
    $(addprefix @,$(SYSCFG_OPT_FILES)) \
    -I../../app \
    -DICALL_NO_APP_EVENTS \
    -mthumb \
    -mlittle-endian \
    -std=gnu99 \
    -std=c99 \
    -ffunction-sections \
    -g \
    -Oz \
    "-I$(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)/source" \
    "-I$(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)/source/ti" \
    "-I$(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)/source/ti/common/cc26xx" \
    "@$(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)/source/ti/ble/stack_util/config/build_components.opt" \
    "@$(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)/source/ti/ble/stack_util/config/factory_config.opt" \
    "-I$(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)/source/third_party/mbedtls/include" \
    "-I$(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)/source/third_party/mbedtls/ti" \
    -DMBEDTLS_CONFIG_FILE="configs/config-hsm.h" \
    -DMBEDTLS_PSA_CRYPTO_CONFIG_FILE="configs/config-psa-crypto-hsm.h" \
    "-I$(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)/source/third_party/mcuboot/boot/bootutil/include" \
    "-I$(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)/source/third_party/mcuboot/boot/ti/source/mcuboot_app" \
    "-I$(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)/source/ti/common/cc26xx/flash_interface" \
    -DNVOCMP_NVPAGES=6 \
    -DNVOCMP_POSIX_MUTEX \
    -DNVOCMP_NWSAMEITEM=1 \
    "-I$(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)/source/ti/drivers/rcl" \
    -Wunused-function \
    "-I$(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)/source/ti/posix/ticlang" \
    -DFREERTOS \
    "-I$(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)/kernel/freertos" \
    "-I$(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)/source/third_party/freertos/include" \
    "-I$(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)/source/third_party/freertos/portable/GCC/ARM_CM0" \
    -mcpu=cortex-m0plus \
    -march=thumbv6m \
    -mfloat-abi=soft \
    -Wall


ifneq ($(CONFIG),RELEASE)
# Add debug flags by default (excluded only when CONFIG=RELEASE)
CFLAGS += $(DFLAGS)
endif

LFLAGS += -Wl,--diag_wrap=off \
    -Wl,--display_error_number \
    -Wl,-x \
    -Wl,-c \
    "-L$(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)/source" \
    ti_utils_build_linker.cmd.genlibs \
    ../../freertos/ticlang/cc2340_freertos.cmd \
    "-Wl,-m,$(NAME).map" \
    -Wl,--rom_model \
    -Wl,--warn_sections \
    "-L$(TICLANG_ARMCOMPILER)/lib" \
    -llibc.a

all: postbuild

.PHONY: postbuild
postbuild: $(NAME).out
	$(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)/tools/common/crc_tool/crc_tool patch-image --elf $(NAME).out --symbol-prefix ti_utils_build_GenMap_sym_CRC_CCFG -o $(NAME).out
	$(TICLANG_ARMCOMPILER)/bin/tiarmobjcopy -O ihex $(NAME).out $(NAME).hex
	$(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)/tools/ble/oad/commands_runner --cfg_file ./ti_ble_oad_postbuild.cfg --compiler ticlang --cg_tool_root $(TICLANG_ARMCOMPILER) --build_dir . --base_name $(NAME) --sdk_dir $(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)

.PHONY: prebuild
prebuild:
	$(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)/tools/ble/oad/commands_runner --cfg_file ./ti_ble_oad_prebuild.cfg --compiler ticlang --build_dir . --gmake_dir ${CCS_INSTALL_ROOT}/utils/bin/gmake --sysconfig_cli ${SYSCONFIG_TOOL} --execute_sysconfig --sdk_dir $(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)

.INTERMEDIATE: syscfg
$(SYSCFG_FILES): syscfg
	@ echo generation complete

syscfg: ../../freertos/soil_sensor.syscfg
	@ echo Generating configuration files...
	$(V) $(SYSCFG_CMD_STUB) --output $(@D) $<


# Helpful hint that the user needs to use a standalone SysConfig installation
$(SYSCONFIG_GUI_TOOL):
	$(error $(dir $(SYSCONFIG_TOOL)) does not contain the GUI framework \
        necessary to launch the SysConfig GUI.  Please set SYSCONFIG_TOOL \
        (in your SDK's imports.mak) to a standalone SysConfig installation \
        rather than one inside CCS)

syscfg-gui: ../../freertos/soil_sensor.syscfg $(SYSCONFIG_GUI_TOOL)
	@ echo Opening SysConfig GUI
	$(V) $(SYSCFG_GUI_CMD_STUB) $<


define C_RULE
$(basename $(notdir $(1))).obj: $(1) $(SYSCFG_H_FILES) prebuild
	@ echo Building $$@
	$(V) $(CC) $(CFLAGS) -c $$< -o $$@
endef

$(foreach c_file,$(SYSCFG_C_FILES),$(eval $(call C_RULE,$(c_file))))

app_app_main.obj: ../../app/app_main.c $(SYSCFG_H_FILES) prebuild
	@ echo Building $@
	$(V) $(CC) $(CFLAGS) -c $< -o $@

app_app_menu.obj: ../../app/app_menu.c $(SYSCFG_H_FILES) prebuild
	@ echo Building $@
	$(V) $(CC) $(CFLAGS) -c $< -o $@

app_Profiles_app_dev_info.obj: ../../app/Profiles/app_dev_info.c $(SYSCFG_H_FILES) prebuild
	@ echo Building $@
	$(V) $(CC) $(CFLAGS) -c $< -o $@

common_Services_dev_info_dev_info_service.obj: $(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)/source/ti/ble/services/dev_info/src/dev_info_service.c $(SYSCFG_H_FILES) prebuild
	@ echo Building $@
	$(V) $(CC) $(CFLAGS) -c $< -o $@

common_BLEAppUtil_bleapputil_task.obj: $(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)/source/ti/ble/app_util/framework/src/bleapputil_task.c $(SYSCFG_H_FILES) prebuild
	@ echo Building $@
	$(V) $(CC) $(CFLAGS) -c $< -o $@

common_BLEAppUtil_bleapputil_init.obj: $(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)/source/ti/ble/app_util/framework/src/bleapputil_init.c $(SYSCFG_H_FILES) prebuild
	@ echo Building $@
	$(V) $(CC) $(CFLAGS) -c $< -o $@

common_BLEAppUtil_bleapputil_process.obj: $(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)/source/ti/ble/app_util/framework/src/bleapputil_process.c $(SYSCFG_H_FILES) prebuild
	@ echo Building $@
	$(V) $(CC) $(CFLAGS) -c $< -o $@

common_BLEAppUtil_bleapputil_stack_callbacks.obj: $(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)/source/ti/ble/app_util/framework/src/bleapputil_stack_callbacks.c $(SYSCFG_H_FILES) prebuild
	@ echo Building $@
	$(V) $(CC) $(CFLAGS) -c $< -o $@

common_BLEAppUtil_bleapputil_timers.obj: $(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)/source/ti/ble/app_util/framework/src/bleapputil_timers.c $(SYSCFG_H_FILES) prebuild
	@ echo Building $@
	$(V) $(CC) $(CFLAGS) -c $< -o $@

common_Startup_osal_icall_ble.obj: ../../common/Startup/osal_icall_ble.c $(SYSCFG_H_FILES) prebuild
	@ echo Building $@
	$(V) $(CC) $(CFLAGS) -c $< -o $@

common_Startup_rom_init.obj: $(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)/source/ti/ble/stack_util/lib_opt/rom_init.c $(SYSCFG_H_FILES) prebuild
	@ echo Building $@
	$(V) $(CC) $(CFLAGS) -c $< -o $@

common_lib_opt_ctrl_opt_rssi_monitor.obj: $(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)/source/ti/ble/stack_util/lib_opt/src/ctrl_opt_rssi_monitor.c $(SYSCFG_H_FILES) prebuild
	@ echo Building $@
	$(V) $(CC) $(CFLAGS) -c $< -o $@

common_lib_opt_ctrl_opt_connection_handover.obj: $(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)/source/ti/ble/stack_util/lib_opt/src/ctrl_opt_connection_handover.c $(SYSCFG_H_FILES) prebuild
	@ echo Building $@
	$(V) $(CC) $(CFLAGS) -c $< -o $@

common_lib_opt_ctrl_opt_dmm.obj: $(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)/source/ti/ble/stack_util/lib_opt/src/ctrl_opt_dmm.c $(SYSCFG_H_FILES) prebuild
	@ echo Building $@
	$(V) $(CC) $(CFLAGS) -c $< -o $@

common_lib_opt_ctrl_opt_dmm_dynamic_priority.obj: $(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)/source/ti/ble/stack_util/lib_opt/src/ctrl_opt_dmm_dynamic_priority.c $(SYSCFG_H_FILES) prebuild
	@ echo Building $@
	$(V) $(CC) $(CFLAGS) -c $< -o $@

common_lib_opt_ctrl_opt_ts_periodic.obj: $(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)/source/ti/ble/stack_util/lib_opt/src/ctrl_opt_ts_periodic.c $(SYSCFG_H_FILES) prebuild
	@ echo Building $@
	$(V) $(CC) $(CFLAGS) -c $< -o $@

common_lib_opt_ctrl_opt_cm.obj: $(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)/source/ti/ble/stack_util/lib_opt/src/ctrl_opt_cm.c $(SYSCFG_H_FILES) prebuild
	@ echo Building $@
	$(V) $(CC) $(CFLAGS) -c $< -o $@

common_lib_opt_ctrl_opt_health_check.obj: $(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)/source/ti/ble/stack_util/lib_opt/src/ctrl_opt_health_check.c $(SYSCFG_H_FILES) prebuild
	@ echo Building $@
	$(V) $(CC) $(CFLAGS) -c $< -o $@

common_lib_opt_ctrl_opt_ble_health.obj: $(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)/source/ti/ble/stack_util/lib_opt/src/ctrl_opt_ble_health.c $(SYSCFG_H_FILES) prebuild
	@ echo Building $@
	$(V) $(CC) $(CFLAGS) -c $< -o $@

common_lib_opt_ctrl_opt_padv.obj: $(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)/source/ti/ble/stack_util/lib_opt/src/ctrl_opt_padv.c $(SYSCFG_H_FILES) prebuild
	@ echo Building $@
	$(V) $(CC) $(CFLAGS) -c $< -o $@

common_lib_opt_ctrl_opt_pscan.obj: $(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)/source/ti/ble/stack_util/lib_opt/src/ctrl_opt_pscan.c $(SYSCFG_H_FILES) prebuild
	@ echo Building $@
	$(V) $(CC) $(CFLAGS) -c $< -o $@

common_lib_opt_ctrl_opt_pawr_scan.obj: $(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)/source/ti/ble/stack_util/lib_opt/src/ctrl_opt_pawr_scan.c $(SYSCFG_H_FILES) prebuild
	@ echo Building $@
	$(V) $(CC) $(CFLAGS) -c $< -o $@

common_lib_opt_ctrl_opt_pawr_advertiser.obj: $(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)/source/ti/ble/stack_util/lib_opt/src/ctrl_opt_pawr_advertiser.c $(SYSCFG_H_FILES) prebuild
	@ echo Building $@
	$(V) $(CC) $(CFLAGS) -c $< -o $@

common_lib_opt_ctrl_opt_cs.obj: $(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)/source/ti/ble/stack_util/lib_opt/src/ctrl_opt_cs.c $(SYSCFG_H_FILES) prebuild
	@ echo Building $@
	$(V) $(CC) $(CFLAGS) -c $< -o $@

common_lib_opt_ctrl_opt_cs_test.obj: $(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)/source/ti/ble/stack_util/lib_opt/src/ctrl_opt_cs_test.c $(SYSCFG_H_FILES) prebuild
	@ echo Building $@
	$(V) $(CC) $(CFLAGS) -c $< -o $@

common_lib_opt_ctrl_opt_initiator.obj: $(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)/source/ti/ble/stack_util/lib_opt/src/ctrl_opt_initiator.c $(SYSCFG_H_FILES) prebuild
	@ echo Building $@
	$(V) $(CC) $(CFLAGS) -c $< -o $@

common_lib_opt_ctrl_opt_connectable.obj: $(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)/source/ti/ble/stack_util/lib_opt/src/ctrl_opt_connectable.c $(SYSCFG_H_FILES) prebuild
	@ echo Building $@
	$(V) $(CC) $(CFLAGS) -c $< -o $@

common_lib_opt_ctrl_opt_scanner.obj: $(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)/source/ti/ble/stack_util/lib_opt/src/ctrl_opt_scanner.c $(SYSCFG_H_FILES) prebuild
	@ echo Building $@
	$(V) $(CC) $(CFLAGS) -c $< -o $@

common_lib_opt_ctrl_opt_power_control.obj: $(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)/source/ti/ble/stack_util/lib_opt/src/ctrl_opt_power_control.c $(SYSCFG_H_FILES) prebuild
	@ echo Building $@
	$(V) $(CC) $(CFLAGS) -c $< -o $@

common_lib_opt_ctrl_opt_adv_conn.obj: $(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)/source/ti/ble/stack_util/lib_opt/src/ctrl_opt_adv_conn.c $(SYSCFG_H_FILES) prebuild
	@ echo Building $@
	$(V) $(CC) $(CFLAGS) -c $< -o $@

common_lib_opt_ctrl_opt_adv_nconn.obj: $(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)/source/ti/ble/stack_util/lib_opt/src/ctrl_opt_adv_nconn.c $(SYSCFG_H_FILES) prebuild
	@ echo Building $@
	$(V) $(CC) $(CFLAGS) -c $< -o $@

common_lib_opt_ctrl_opt_ae.obj: $(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)/source/ti/ble/stack_util/lib_opt/src/ctrl_opt_ae.c $(SYSCFG_H_FILES) prebuild
	@ echo Building $@
	$(V) $(CC) $(CFLAGS) -c $< -o $@

common_lib_opt_ctrl_opt_adaptivity.obj: $(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)/source/ti/ble/stack_util/lib_opt/src/ctrl_opt_adaptivity.c $(SYSCFG_H_FILES) prebuild
	@ echo Building $@
	$(V) $(CC) $(CFLAGS) -c $< -o $@

common_lib_opt_ctrl_opt_past_sender.obj: $(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)/source/ti/ble/stack_util/lib_opt/src/ctrl_opt_past_sender.c $(SYSCFG_H_FILES) prebuild
	@ echo Building $@
	$(V) $(CC) $(CFLAGS) -c $< -o $@

common_lib_opt_ctrl_opt_past_receiver.obj: $(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)/source/ti/ble/stack_util/lib_opt/src/ctrl_opt_past_receiver.c $(SYSCFG_H_FILES) prebuild
	@ echo Building $@
	$(V) $(CC) $(CFLAGS) -c $< -o $@

common_lib_opt_ctrl_opt_vendor_specific_cmd.obj: $(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)/source/ti/ble/stack_util/lib_opt/src/ctrl_opt_vendor_specific_cmd.c $(SYSCFG_H_FILES) prebuild
	@ echo Building $@
	$(V) $(CC) $(CFLAGS) -c $< -o $@

common_lib_opt_ctrl_opt_ext_vendor_specific_cmd.obj: $(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)/source/ti/ble/stack_util/lib_opt/src/ctrl_opt_ext_vendor_specific_cmd.c $(SYSCFG_H_FILES) prebuild
	@ echo Building $@
	$(V) $(CC) $(CFLAGS) -c $< -o $@

common_lib_opt_ctrl_opt_coex.obj: $(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)/source/ti/ble/stack_util/lib_opt/src/ctrl_opt_coex.c $(SYSCFG_H_FILES) prebuild
	@ echo Building $@
	$(V) $(CC) $(CFLAGS) -c $< -o $@

common_lib_opt_ctrl_opt_legacy_cmd.obj: $(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)/source/ti/ble/stack_util/lib_opt/src/ctrl_opt_legacy_cmd.c $(SYSCFG_H_FILES) prebuild
	@ echo Building $@
	$(V) $(CC) $(CFLAGS) -c $< -o $@

common_lib_opt_host_opt_gap_bond_mgr.obj: $(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)/source/ti/ble/stack_util/lib_opt/src/host_opt_gap_bond_mgr.c $(SYSCFG_H_FILES) prebuild
	@ echo Building $@
	$(V) $(CC) $(CFLAGS) -c $< -o $@

common_lib_opt_host_opt_gap_pawr_scan.obj: $(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)/source/ti/ble/stack_util/lib_opt/src/host_opt_gap_pawr_scan.c $(SYSCFG_H_FILES) prebuild
	@ echo Building $@
	$(V) $(CC) $(CFLAGS) -c $< -o $@

common_lib_opt_host_opt_gap_pawr_adv.obj: $(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)/source/ti/ble/stack_util/lib_opt/src/host_opt_gap_pawr_adv.c $(SYSCFG_H_FILES) prebuild
	@ echo Building $@
	$(V) $(CC) $(CFLAGS) -c $< -o $@

common_lib_opt_host_opt_gap_past_receiver.obj: $(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)/source/ti/ble/stack_util/lib_opt/src/host_opt_gap_past_receiver.c $(SYSCFG_H_FILES) prebuild
	@ echo Building $@
	$(V) $(CC) $(CFLAGS) -c $< -o $@

common_lib_opt_host_opt_gap_past_sender.obj: $(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)/source/ti/ble/stack_util/lib_opt/src/host_opt_gap_past_sender.c $(SYSCFG_H_FILES) prebuild
	@ echo Building $@
	$(V) $(CC) $(CFLAGS) -c $< -o $@

common_Drivers_NV_crc.obj: $(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)/source/ti/common/nv/crc.c $(SYSCFG_H_FILES) prebuild
	@ echo Building $@
	$(V) $(CC) $(CFLAGS) -c $< -o $@

common_Drivers_NV_nvocmp.obj: $(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)/source/ti/common/nv/nvocmp.c $(SYSCFG_H_FILES) prebuild
	@ echo Building $@
	$(V) $(CC) $(CFLAGS) -c $< -o $@

common_iCall_icall.obj: $(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)/source/ti/ble/stack_util/icall/app/src/icall.c $(SYSCFG_H_FILES) prebuild
	@ echo Building $@
	$(V) $(CC) $(CFLAGS) -c $< -o $@

common_iCall_icall_user_config.obj: $(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)/source/ti/ble/stack_util/icall/app/src/icall_user_config.c $(SYSCFG_H_FILES) prebuild
	@ echo Building $@
	$(V) $(CC) $(CFLAGS) -c $< -o $@

common_config_ble_user_config.obj: $(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)/source/ti/ble/app_util/config/src/ble_user_config.c $(SYSCFG_H_FILES) prebuild
	@ echo Building $@
	$(V) $(CC) $(CFLAGS) -c $< -o $@

common_config_hci_supported_cmd.obj: $(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)/source/ti/ble/app_util/config/src/hci_supported_cmd.c $(SYSCFG_H_FILES) prebuild
	@ echo Building $@
	$(V) $(CC) $(CFLAGS) -c $< -o $@

app_app_peripheral.obj: ../../app/app_peripheral.c $(SYSCFG_H_FILES) prebuild
	@ echo Building $@
	$(V) $(CC) $(CFLAGS) -c $< -o $@

app_app_connection.obj: ../../app/app_connection.c $(SYSCFG_H_FILES) prebuild
	@ echo Building $@
	$(V) $(CC) $(CFLAGS) -c $< -o $@

app_app_data.obj: ../../app/app_data.c $(SYSCFG_H_FILES) prebuild
	@ echo Building $@
	$(V) $(CC) $(CFLAGS) -c $< -o $@

app_soil_sensor.obj: ../../app/soil_sensor.c $(SYSCFG_H_FILES) prebuild
	@ echo Building $@
	$(V) $(CC) $(CFLAGS) -c $< -o $@

app_battery_gauge.obj: ../../app/battery_gauge.c $(SYSCFG_H_FILES) prebuild
	@ echo Building $@
	$(V) $(CC) $(CFLAGS) -c $< -o $@


common_Util_util.obj: $(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)/source/ti/ble/app_util/common/src/util.c $(SYSCFG_H_FILES) prebuild
	@ echo Building $@
	$(V) $(CC) $(CFLAGS) -c $< -o $@

HealthToolkit_debugInfo.obj: $(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)/source/ti/ble/stack_util/health_toolkit/src/debugInfo.c $(SYSCFG_H_FILES) prebuild
	@ echo Building $@
	$(V) $(CC) $(CFLAGS) -c $< -o $@

HealthToolkit_ble_sys_stat.obj: $(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)/source/ti/ble/stack_util/health_toolkit/src/ble_sys_stat.c $(SYSCFG_H_FILES) prebuild
	@ echo Building $@
	$(V) $(CC) $(CFLAGS) -c $< -o $@

common_MenuModule_menu_module.obj: $(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)/source/ti/ble/app_util/menu/src/menu_module.c $(SYSCFG_H_FILES) prebuild
	@ echo Building $@
	$(V) $(CC) $(CFLAGS) -c $< -o $@

app_app_soil_sensor.obj: ../../app/app_soil_sensor.c $(SYSCFG_H_FILES) prebuild
	@ echo Building $@
	$(V) $(CC) $(CFLAGS) -c $< -o $@

app_soil_history.obj: ../../app/soil_history.c $(SYSCFG_H_FILES) prebuild
	@ echo Building $@
	$(V) $(CC) $(CFLAGS) -c $< -o $@

app_Profiles_soil_sensor_profile.obj: ../../app/Profiles/soil_sensor_profile.c $(SYSCFG_H_FILES) prebuild
	@ echo Building $@
	$(V) $(CC) $(CFLAGS) -c $< -o $@

app_Profiles_battery_service.obj: ../../app/Profiles/battery_service.c $(SYSCFG_H_FILES) prebuild
	@ echo Building $@
	$(V) $(CC) $(CFLAGS) -c $< -o $@

app_Profiles_app_oad.obj: ../../app/Profiles/app_oad.c $(SYSCFG_H_FILES) prebuild
	@ echo Building $@
	$(V) $(CC) $(CFLAGS) -c $< -o $@

common_Services_oad_oad_service.obj: $(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)/source/ti/ble/services/oad/src/oad_service.c $(SYSCFG_H_FILES) prebuild
	@ echo Building $@
	$(V) $(CC) $(CFLAGS) -c $< -o $@

common_Services_oad_oad_reset_service.obj: $(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)/source/ti/ble/services/oad/src/oad_reset_service.c $(SYSCFG_H_FILES) prebuild
	@ echo Building $@
	$(V) $(CC) $(CFLAGS) -c $< -o $@

common_Profiles_oad_oad_profile.obj: $(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)/source/ti/ble/profiles/oad/src/oad_profile.c $(SYSCFG_H_FILES) prebuild
	@ echo Building $@
	$(V) $(CC) $(CFLAGS) -c $< -o $@

common_Profiles_oad_ms_oad_profile.obj: $(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)/source/ti/ble/profiles/oad/src/ms_oad_profile.c $(SYSCFG_H_FILES) prebuild
	@ echo Building $@
	$(V) $(CC) $(CFLAGS) -c $< -o $@

common_Util_SwUpdate_sw_update.obj: $(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)/source/ti/ble/app_util/sw_update/src/sw_update.c $(SYSCFG_H_FILES) prebuild
	@ echo Building $@
	$(V) $(CC) $(CFLAGS) -c $< -o $@

common_Util_SwUpdate_flash_interface_oad.obj: $(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)/source/ti/ble/app_util/sw_update/src/flash_interface_oad.c $(SYSCFG_H_FILES) prebuild
	@ echo Building $@
	$(V) $(CC) $(CFLAGS) -c $< -o $@

common_iCall_icall_POSIX.obj: $(SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR)/source/ti/ble/stack_util/icall/app/src/icall_POSIX.c $(SYSCFG_H_FILES) prebuild
	@ echo Building $@
	$(V) $(CC) $(CFLAGS) -c $< -o $@

common_Startup_main_freertos.obj: ../../common/Startup/main_freertos.c $(SYSCFG_H_FILES) prebuild
	@ echo Building $@
	$(V) $(CC) $(CFLAGS) -c $< -o $@

$(NAME).out: $(OBJECTS)
	@ echo linking $@
	$(V) $(LNK) -Wl,-u,_c_int00 $(OBJECTS)  $(LFLAGS) -o $(NAME).out

clean:
	@ echo Cleaning...
	$(V) $(RM) $(OBJECTS) > $(DEVNULL) 2>&1
	$(V) $(RM) $(NAME).out > $(DEVNULL) 2>&1
	$(V) $(RM) $(NAME).map > $(DEVNULL) 2>&1
	$(V) $(RM) $(call SLASH_FIXUP,$(SYSCFG_FILES)) > $(DEVNULL) 2>&1
