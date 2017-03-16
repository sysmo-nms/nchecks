run:
	./gradlew installDist
	@#./build/install/nchecks/bin/nchecks --no-snmp CheckHTTP uri=http://www.google.fr
	@#./build/install/nchecks/bin/nchecks --snmp CheckIfErrors host=127.0.0.1 snmp_port=161 snmp_seclevel=noAuthNoPriv snmp_version=2c snmp_retries=2 snmp_timeout=2000 snmp_community=public if_selection=0,1 warning_threshold=1 critical_threshold=10 target_id=testid
	@#./build/install/nchecks/bin/nchecks --snmp CheckLinuxCPULoad host=127.0.0.1 snmp_port=161 snmp_seclevel=noAuthNoPriv snmp_version=2c snmp_retries=2 snmp_timeout=2000 snmp_community=public warning_threshold=1 critical_threshold=10 target_id=testid
	./build/install/nchecks/bin/nchecks --snmp CheckLinuxMemory host=127.0.0.1 snmp_port=161 snmp_seclevel=noAuthNoPriv snmp_version=2c snmp_retries=2 snmp_timeout=2000 snmp_community=public total_mem_warning_threshold=70 total_mem_critical_threshold=90 target_id=testid
