# Copyright 2015-2016 Sebastien Serre <ssbx@sysmo.io>
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
require 'java'
java_import 'io.sysmo.nchecks.Reply'
java_import 'io.sysmo.nchecks.Status'
java_import 'io.sysmo.nchecks.states.PerformanceGroupState'
java_import 'io.sysmo.nchecks.snmp.TableWalker'
java_import 'java.lang.Integer'

$IF_INDEX      = "1.3.6.1.2.1.2.2.1.1"
$IF_IN_ERRORS  = "1.3.6.1.2.1.2.2.1.14"
$IF_OUT_ERRORS = "1.3.6.1.2.1.2.2.1.20"

def check(query) # query is io.sysmo.nchecks.Query
  reply = Reply.new()

  #
  # get arguments: if_selection, warning_threshold, critical_threshold
  #
  begin 
      arg_if_selection = query.get("if_selection")
      arg_warning_threshold = query.get("warning_threshold")
      arg_critical_threshold = query.get("critical_threshold")
  rescue Exception => e
      reply.setStatus(Status::ERROR)
      reply.setReply("Missing or wrong argument.")
      return reply
  end


  #
  # Get the state or create it
  #
  pg_state = query.getState()
  if (pg_state == nil)
      pg_state = PerformanceGroupState.new()
  end


  #
  # Initialize SNMP walker
  #
  walker = TableWalker.new()
  walker.addColumn($IF_INDEX)
  walker.addColumn($IF_IN_ERRORS)
  walker.addColumn($IF_OUT_ERRORS)
  arg_if_selection_list = arg_if_selection.asString().split(",")
  arg_if_selection_list.each { |index|
      walker.addIndex(index)
  }

  #
  # Walk the target and fill PerformanceGroupState and reply
  #
  snmp_reply = walker.walk(query)
  snmp_reply.each { |event|
      variable_bindings = event.getColumns()
      interface_index = variable_bindings[0].getVariable().toInt()

      if arg_if_selection_list.include?(Integer.toString(interface_index))
          errsIn  = variable_bindings[1].getVariable().toLong()
          errsOut = variable_bindings[2].getVariable().toLong()
          reply.putPerformance(interface_index, "IfInErrors",  errsIn)
          reply.putPerformance(interface_index, "IfOutErrors", errsOut)
          pg_state.put(interface_index, errsIn + errsOut)
      end
  }


  #
  # Calculate state and set reply info
  #
  new_status = Status::OK
  begin

      warning_limit  = arg_warning_threshold.asInteger()
      critical_limit = arg_critical_threshold.asInteger()
      new_status = pg_state.computeStatusMaps(warning_limit, critical_limit)

      if    new_status == Status::OK
          reply.setReply("CheckIfErrors OK ")
      elsif new_status == Status::UNKNOWN
          reply.setReply("CheckIfErrors UNKNOWN. No enough data to set sensible status.")
      elsif new_status == Status::WARNING
          reply.setReply("CheckIfErrors WARNING have found errors!")
      elsif new_status == Status::CRITICAL
          reply.setReply("CheckIfErrors CRITICAL have found errors!")
      end

  rescue Exception => e
      new_status = Status::ERROR
      reply.setReply("CheckIfErrors: #{e.message}")
  end

  reply.setState(pg_state)
  reply.setStatus(new_status)

  return reply

end
