/********************************************************************
 *  As a subpart of Twake Mail, this file is edited by Linagora.    *
 *                                                                  *
 *  https://twake-mail.com/                                         *
 *  https://linagora.com                                            *
 *                                                                  *
 *  This file is subject to The Affero Gnu Public License           *
 *  version 3.                                                      *
 *                                                                  *
 *  https://www.gnu.org/licenses/agpl-3.0.en.html                   *
 *                                                                  *
 *  This program is distributed in the hope that it will be         *
 *  useful, but WITHOUT ANY WARRANTY; without even the implied      *
 *  warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR         *
 *  PURPOSE. See the GNU Affero General Public License for          *
 *  more details.                                                   *
 ********************************************************************/

package com.linagora.tmail.healthcheck;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.task.TaskType;
import org.apache.james.util.DurationParser;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;

public record TasksHealthCheckConfiguration(Map<TaskType, Duration> taskTypeDurationMap) {
    public static final String TASKS_EXECUTION = "healthcheck.tasks.execution";
    public static final TasksHealthCheckConfiguration DEFAULT_CONFIGURATION = new TasksHealthCheckConfiguration(Map.of());

    public static TasksHealthCheckConfiguration from(Configuration configuration) {
        return new TasksHealthCheckConfiguration(parseTasksExecutionMap(configuration.getString(TASKS_EXECUTION, "")));
    }

    @VisibleForTesting
    public static TasksHealthCheckConfiguration from(String string) {
        return new TasksHealthCheckConfiguration(parseTasksExecutionMap(string));
    }

    static Map<TaskType, Duration> parseTasksExecutionMap(String string) {
        Map<String, String> stringToStringMap = Splitter.on(",")
                .omitEmptyStrings()
                .withKeyValueSeparator(":")
                .split(string);

        return stringToStringMap.entrySet()
                .stream()
                .map(entry -> Pair.of(TaskType.of(entry.getKey()), DurationParser.parse(entry.getValue())))
                .collect(Collectors.toMap(Pair::getKey, Pair::getValue));
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof TasksHealthCheckConfiguration that) {
            return Objects.equals(this.taskTypeDurationMap, that.taskTypeDurationMap);
        }
        return false;
    }

}
