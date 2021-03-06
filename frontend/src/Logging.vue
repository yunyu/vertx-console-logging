<template>
    <div class="logging-page row-no-padding">
        <div class="loggers-wrapper col-md-4">
            <div class="toolbar-pf toolbar-pf-actions loggers-toolbar">
                <div class="toolbar-pf-filter">
                    <label for="filter" class="sr-only">Filter</label>
                    <input type="text" id="filter" class="form-control" placeholder="Filter" v-model="filterQuery">
                </div>
            </div>
            <div class="loggers-display">
                <div class="loggers-entries">
                    <div class="logger-entry" v-for="logger in filteredLoggers" :key="logger.name">
                        <div class="logger-entry-checkbox">
                            <input type="checkbox" :checked="!hiddenStatuses[logger.name]" :disabled="hiddenStatuses[logger.name] === 1" v-on:click="updateHiddenLoggers(logger.name, $event)">
                        </div>
                        <div class="logger-entry-name">{{ logger.name }}</div>
                        <div class="logger-entry-level">
                            <v-select v-model="logger.effectiveLevel" :options="levels" :buttonClass="getButtonClassForLevel(logger.effectiveLevel)" v-on:select="updateLogger(logger, $event)">
                            </v-select>
                        </div>
                    </div>
                </div>
            </div>
        </div>
        <div class="log-wrapper col-md-8">
            <log-display :hiddenStatuses="hiddenStatuses" eventBusUrl="/loggerproxy/"></log-display>
        </div>
    </div>
</template>

<style lang="scss">
.logging-page,
.loggers-wrapper,
.log-wrapper {
    height: 100%;
}

.loggers-toolbar {
    background-color: #f5f5f5;
    background-image: linear-gradient(to bottom, #fafafa 0, #f5f5f5 100%);
    padding: 10px 20px;
    margin-bottom: 0px;

    .toolbar-pf-filter {
        min-width: 60%;
    }
}

.loggers-display {
    height: calc(100% - 47px);
    overflow-y: scroll;
    background-color: #fff;

    .loggers-entries {
        .logger-entry {
            padding: 10px;
            margin: 0px 10px;
            border-top: 1px solid #ededed;
            display: -webkit-box;
            display: -ms-flexbox;
            display: flex;

            &:hover {
                background-color: #ededed;
            }

            &:first-child {
                border-top: none;
            }

            .logger-entry-checkbox {
                border-right: 1px solid #d1d1d1;
                padding-right: 10px;
                margin-right: 10px;
                input {
                    margin-top: 6px;
                }
            }

            .logger-entry-name {
                line-height: 24px;
                word-break: break-all;
                padding-right: 10px;
            }

            .logger-entry-level {
                margin-left: auto;
                .bootstrap-select {
                    width: 80px;
                }
            }
        }
    }
}
</style>


<script>
import Loggers from './loggers.js';
import LogDisplay from './LogDisplay.vue';

Loggers.initialize('/loggers');

const ROOT = 'ROOT';

export default {
    name: 'Logging',
    components: {
        'log-display': LogDisplay
    },
    beforeCreate() {
        this.levels = ['OFF', 'ERROR', 'WARN', 'INFO', 'DEBUG', 'TRACE', 'ALL'];
    },
    mounted() {
        this.loggersCallback = loggers => this.loggers = loggers;
        Loggers.addCallback(this.loggersCallback);
    },
    beforeDestroy() {
        Loggers.removeCallback(this.loggersCallback);
    },
    data() {
        return {
            filterQuery: '',
            loggers: [],
            hiddenLoggers: [],
            hiddenStatuses: {}
        }
    },
    computed: {
        filteredLoggers() {
            if (this.filterQuery.length === 0) {
                return this.loggers.map(el => Object.assign({}, el));
            } else {
                return this.loggers.filter(el => el.name.toLowerCase().includes(this.filterQuery.toLowerCase())).map(el => Object.assign({}, el));
            }
        }
    },
    methods: {
        updateLogger(logger, level) {
            logger.effectiveLevel = level;
            this.$http.post(window.location.pathname + '/loggers/' + logger.name + '/update', { level: logger.effectiveLevel, include: 'all' })
                .then(response => response.data)
                .then(this.loggersCallback);
        },
        getHiddenStatus(loggerName) {
            for (let hiddenLoggerName of this.hiddenLoggers) {
                if (hiddenLoggerName === loggerName) {
                    return 2;
                } else if (hiddenLoggerName === ROOT || loggerName.startsWith(hiddenLoggerName)) {
                    return 1;
                }
            }
            return 0;
        },
        updateHiddenLoggers(loggerName, event) {
            if (!event.target.checked) {
                this.hiddenLoggers.push(loggerName);
            } else {
                this.hiddenLoggers.splice(this.hiddenLoggers.findIndex(el => el === loggerName), 1);
            }
        },
        getButtonClassForLevel(level) {
            if (level === 'ALL') {
                return 'btn-primary';
            } else if (level === 'WARN') {
                return 'btn-warning';
            } else if (level === 'ERROR') {
                return 'btn-danger';
            } else {
                return 'btn-default';
            }
        }
    },
    watch: {
        // Selective reactivity for performance reasons
        hiddenLoggers() {
            const statuses = {};
            for (var logger of this.loggers) {
                statuses[logger.name] = this.getHiddenStatus(logger.name);
            }
            this.hiddenStatuses = statuses;
        }
    }
}
</script>