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

require 'net/http'

def check(query) # query is io.sysmo.nchecks.Query

  reply  = Reply.new()

  # Extract arguments from java Query -> Argument {asString|asInteger}
  uristr  = query.get("uri").asString()

  begin
    uri  = URI(uristr)
    http = Net::HTTP.new(uri.host, uri.port)
  rescue Exception => ex
    reply.setStatus(Reply::STATUS_ERROR)
    reply.setReply("#{ex.class} #{ex.message}")
    return reply
  end

  # check MUST reply within 15 seconds
  http.read_timeout = 5
  http.open_timeout = 5
  repstr = nil

  begin
    start  = Time.now

    if uri.path.eql? ""
      path = "/"
    else
      path = uri.path
    end

    response = http.get(path)
    finish = Time.now
    diff   = (finish - start) * 1000

    # will raise an error if code is not 2**
    value = response.value()

    reply.setStatus(Status::OK)
    reply.setReply("HTTP Get successfull response")
    reply.putPerformance("ReplyDuration", diff)

  rescue Net::HTTPServerException => ex
    reply.setStatus(Status::CRITICAL)
    reply.setReply(ex.message())

  rescue Timeout::Error => ex
    reply.setStatus(Status::CRITICAL)
    reply.setReply(ex.message)

  rescue Errno::ECONNREFUSED => ex
    reply.setStatus(Status::CRITICAL)
    reply.setReply("Connection refused")

  rescue Exception => ex
    reply.setStatus(Status::ERROR)
    reply.setReply("#{ex.class} #{ex.message}")

  end

  return reply

end
