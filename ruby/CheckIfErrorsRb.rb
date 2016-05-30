# Sysmo NMS Network Management and Monitoring solution (http://www.sysmo.io)
#
# Copyright (c) 2012-2015 Sebastien Serre <ssbx@sysmo.io>
#
# This file is part of Sysmo NMS.
#
# Sysmo NMS is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# Sysmo NMS is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with Foobar.  If not, see <http://www.gnu.org/licenses/>.
require 'java'
java_import 'io.sysmo.nchecks.Reply'
java_import 'io.sysmo.nchecks.Status'
java_import 'io.sysmo.nchecks.states.PerformanceGroupState'
java_import 'io.sysmo.nchecks.snmp.Walker'

$IF_INDEX = "1.3.6.1.2.1.2.2.1.1";
$IF_IN_ERRORS = "1.3.6.1.2.1.2.2.1.14";
$IF_OUT_ERRORS = "1.3.6.1.2.1.2.2.1.20";

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
      reply.setStatus(Status::ERROR);
      reply.setReply("Missing or wrong argument.")
      return reply
  end


  #
  # Get the state or create it
  #
  # WARNING BUG HERE java nullpointerexception
  pg_state = query.getState()
  if (pg_state == nil)
      pg_state = PerformanceGroupState.new()
  end


  #
  # Inialize SNMP walker
  #
  walker = Walker.new()
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
  begin
      snmp_reply = walker.walk(query)
      snmp_reply.each { |event|
          variable_bindings = event.getColumns()
          interface_index = variable_bindings[0].getVariable().toInt()


          if arg_if_selection_list.include?(Integer.toString(interface_index))
              errsIn = variable_bindings[1].getVariable().toLong()
              errsOut = variable_bindings[2].getVariable().toLong()
              reply.putPerformance(interface_index, "IfInErrors",  errsIn)
              reply.putPerformance(interface_index, "IfOutErrors", errsOut)
              pg_state.put(interface_index, errsIn + errsOut)
          end

      }
  rescue Exception => e
      reply.setStatus(Status::ERROR);
      reply.setReply("SNMP walk failed." + e)
      return reply
  end

  #
  # caculcate state and set reply info
  #
  new_status = pg_state.computeStatusMaps(
      arg_warning_threshold.asInteger(), arg_critical_threshold.asInteger())
  if    new_status == Status::OK
      reply.setReply("CheckIfErrors OK")
  elsif new_status == Status::UNKNOWN
      reply.setReply("CheckIfErrors UNKNOWN. No enough data to set sensible status.")
  elsif new_status == Status::WARNING
      reply.setReply("CheckIfErrors WARNING have found errors!")
  elsif new_status == Status::CRITICAL
      reply.setReply("CheckIfErrors CRITICAL have found errors!")
  end

  reply.setState(pg_state)
  reply.setStatus(new_status)

  return reply

end
