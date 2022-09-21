//Environmental comfort
import groovy.time.*

String getVersionNum() { return "0.0.1" }
String getVersionLabel() { return "Environmental Comfort, version ${getVersionNum()}" }
String appName() { return "Environmental Comfort"}
ArrayList getSetPoints(String name) { 
    def setpoints = [ "neutral_pvm": ["Cooling neutral temperature","Heating neutral temperature"],
                 "utci": ["Neutral temperature"],
                 "ashrae55": ["Neutral temperature"],
                 "ashrae55_conditioned": ["Neutral temperature"],
                 "en15251": ["Neutral temperature"],
                 "en15251_conditioned": ["Neutral temperature"]
                ]
    return setpoints.get(name)
}

definition(
    name:             appName(),
    namespace:         "env_comfort",
    author:         "Matt Groeninger",
    description:     "Sets temperature sensor based on different environmental options.",
    iconUrl:         "",
    iconX2Url:         "",
    singleInstance:    false
)

preferences {
    page(name: "mainPage")
    page(name: "comfort")
}

def mainPage() {
    def units = getTemperatureScale()


    dynamicPage(name: "mainPage", title: "${getVersionLabel()}", nextPage: "comfort", uninstall: true) {
          String defaultLabel = appName()
        section(title: "<b>General</b>") {
               label(title: "Name", required: true, defaultValue: defaultLabel)
            if (!app.label) {
                app.updateLabel(defaultLabel)
                state.appDisplayName = defaultLabel
            }
            input "logging", "enum", title: "Log Level", required: false, defaultValue: "INFO", options: ["TRACE", "DEBUG", "INFO", "WARN", "ERROR"]
        }
        def h_outdoors = null
        def t_indoors = null
        def h_indoors = null
        def t_operative = null
        def indoor_metrics = null

        section(title: "<b>Outdoor temperature</b>" ) {
            input(name: 'outdoor_temp', type: 'capability.temperatureMeasurement', title: "Weather sensor for temperature", required: true, multiple: false, submitOnChange: true)
        }
        section(title: "<b>Outdoor humidity</b>" ) {
            input(name: 'outdoor_humidity', type: 'capability.relativeHumidityMeasurement', title: "Weather sensor for relative humidity", required: true, multiple: false, submitOnChange: true)
        }
        section(title: "<b>Outdoor pressure</b>" ) {
            input(name: 'pressure_sensor_type', type: 'enum', options: ['none': 'None','pressureMeasurement':'Barometer', 'relativeHumidityMeasurement':'Humidistat', 'temperatureMeasurement':'Thermometer'], title: 'What type of sensor has your pressure data?', required: true, multiple: false, defaultValue: 'none', submitOnChange: true)
            paragraph "Note: if an outdoor pressure sensor is not selected the elevation will be used to calculate a general pressure."
            if (settings?.pressure_sensor_type && settings.pressure_sensor_type != "none") {
                input(name: 'outdoor_pressure', type: "capability.${settings.pressure_sensor_type}", title: "Weather sensor for outdoor pressure", required: false, multiple: false, submitOnChange: true)
                if (settings.pressure_sensor_type == 'pressureMeasurement') {
                    settings.pressure_attribute = "pressure"
                } else {
                    if (settings?.outdoor_pressure) {
                        def attributes = []
                        settings.outdoor_pressure.getSupportedAttributes().each{  
                            attributes.add("${it}")
                        }
                        input(name: 'pressure_attribute', type: 'enum', options: attributes, title: "Which attribute on sensor ${outdoor_pressure.displayName} contains pressure data?", required: (settings.pressure_sensor_type != 'pressureMeasurement' && settings.pressure_sensor_type != 'none') , multiple: false, defaultValue: 'pressure', submitOnChange: true)
                    }
                }
            }
            if (!settings?.outdoor_pressure || settings.pressure_sensor_type == 'none') {
                input(name: "elevation", type: "number", title: "Elevation (meters)", required: false, defaultValue: 0 , submitOnChange: true)
                paragraph "Please enter the elevation of your location, in meters."    
            }
        }
/*             section(title: "<b>Outdoor cloud cover</b>" ) {
            input(name: 'cloudiness_type', type: 'enum', options: ['none':'None','temperatureMeasurement':'Thermometer', 'illuminanceMeasurement':'Light/illuminance sensor'], title: 'What type of sensor has your cloudiness data?', required: false, multiple: false, defaultValue: 'none', submitOnChange: true)
            if (settings?.cloudiness_type && settings.cloudiness_type != "none") {
                input(name: 'outdoor_cloudiness', type: "capability.${settings.cloudiness_type}", title: "Sensor for custom cloud index", required: false, multiple: false, submitOnChange: true)
                if (settings?.outdoor_cloudiness) {
            def attributes = []
            settings.outdoor_cloudiness.getSupportedAttributes().each{  
                attributes.add("${it}")
            }
            input(name: 'cloudiness_attribute', type: 'enum', options: attributes, title: 'Which attribute contains cloud data?', required: false, multiple: false, defaultValue: 'cloudiness', submitOnChange: true)
                }
            }
        }
*/
        section(title: "<b>Outdoor measurements</b>" ) {
            if (outdoor_configured()) {
                t_outdoors = settings.outdoor_temp.currentTemperature
                h_outdoors = getOutdoorHumidity()
                def pressure = get_pressure()
                def pressure_message = " and the atmospheric pressure (as ${pressure.method}) is ${pressure.atmos * 0.01} hPa."
                paragraph "The current outdoor temperature from device ${outdoor_temp.displayName} is ${t_outdoors}°${units} and the relative humidity is ${h_outdoors}%${pressure_message}"
            }
        }
        section(title: "<b>Indoor temperature/humidity</b>" ) {
            input(name: 'indoorThermostat', type: 'capability.thermostat', title: "Thermostat", required: true, multiple: false, submitOnChange: true)
            if (settings?.indoorThermostat && app.label == defaultLabel) {
                app.updateLabel(defaultLabel + " - " + settings.indoorThermostat.label)
                state.appDisplayName = defaultLabel + " - " + settings.indoorThermostat.label
            }
            input(name: 'indoorHumidistat', type: 'capability.relativeHumidityMeasurement', title: "Relative Humidity Sensors", required: true, multiple: true, submitOnChange: true)
            if (settings?.indoorHumidistat && settings.indoorHumidistat.size() > 1) {
                input(name: 'multiHumidRead', type: 'enum', options: ['average', 'highest', 'lowest'], title: 'Multiple sensor method', required: (settings.indoorHumidistat.size() > 1), multiple: false, defaultValue: 'average', submitOnChange: true)
            }
            if (indoor_configured()) {
                t_indoors = indoorThermostat.currentTemperature
                h_indoors = getIndoorHumidity()
                paragraph "The current indoor temperature at ${indoorThermostat.displayName} is ${t_indoors}°${units} and the relative humidity is ${h_indoors}%"
            }
        }
        section(title: "<b>Current conditions</b>") {
            if (indoor_configured()) {
                indoor_metrics = getPsycoMetricsLocalTemp(false)
                paragraph "Indoor air metrics are: ${indoor_metrics}"
            }
            if (outdoor_configured()) {
                paragraph "Outdoor air metrics are: ${getPsycoMetricsLocalTemp()}"
            }
        }
    }
}

def comfort() {
    def coolPmv = [
            (-1.0): 'Very cool (-1.0)',
            (-0.5): 'Cool (-0.5)',
            0.0: 'Slightly cool (0.0)',
            0.5: 'Comfortable (0.5)',
            0.64: 'Eco (0.64)',
            0.8: 'Slightly warm (0.8)',
            1.9: 'Warm (1.9)',
            'custom': 'Custom'
    ]
    def heatPmv = [
            1.0: 'Very warm (1.0)',
            0.5: 'Warm (0.5)',
            0.0: 'Slightly warm (0.0)',
            (-0.5): 'Comfortable (-0.5)',
            (-0.64): 'Eco (-0.64)',
            (-1.0): 'Slightly cool (-1.0)',
            (-2.3): 'Cool (-2.3)',
            'custom': 'Custom'
    ]
    def metobolicRates = [
            0.7: 'Sleeping (0.7)',
            0.8: 'Reclining (0.8)',
            1.0: 'Seated, quiet (1.0)',
            1.1: 'Typing (1.1)',
            1.2: 'Standing, relaxed (1.2)',
            1.4: 'Seated, light activity (1.4)',
            1.7: 'Walking about (1.7)',
            1.8: 'Cooking (1.8)',
            2.1: 'Lifting/packing (2.1)',
            2.7: 'House cleaning (2.7)',
            'custom': 'Custom'
    ]
    def typicalEnsembles = [
             0.0: 'Naked (0.0)',
            0.04: 'Swimwear/underwear (0.04)',
            0.24: 'Walking shorts, t-sleeve shirt, socks and shoes (0.24)',
            0.36: 'Walking shorts, short-sleeve shirt (0.36)',
             0.4: 'Sweatpants, t-shirt (0.4)',
             0.5: 'Typical summer indoor clothing (0.5)',
            0.54: 'Knee-length skirt, short-sleeve shirt, sandals (0.54)',
            0.57: 'Trousers, short-sleeve shirt, socks and shoes (0.57)',
            0.61: 'Trousers, long-sleeve shirt, socks and shoes (0.61)',
            0.67: 'Knee-length skirt, long-sleeve shirt, full slip, shoes (0.67)',
            0.74: 'Sweatpants, t-shirt, long-sleeve sweatshirt (0.74)',
            0.78: 'Sweatpants, t-shirt, long-sleeve sweatshirt, socks and shoes (0.78)',
            0.96: 'Jacket, Trousers, long-sleeve shirt (0.96)',
             1.0: 'Typical Winter indoor clothing (1.0)',
             2.9: 'Summer lightweight duvet [0.64-2.9] (2.9)',
            'custom': 'Custom'
    ]
    def units = getTemperatureScale()

    def indoor_metrics = getPsycoMetrics(false)
    log("indoor metrics: ${indoor_metrics}","debug")
    def outdoor_metrics = getPsycoMetrics()
    log("outdoor metrics: ${outdoor_metrics}","debug")
    def t_operative = round(calc_operative_temp(indoor_metrics.drybulb,outdoor_metrics.drybulb,settings.radiant_adjustment),1)

    def t_meanr = indoor_metrics.iWBGT as BigDecimal
    def humidity = indoor_metrics.relHum
    def velocity = indoor_metrics.velocity

    dynamicPage(name: "comfort", title: "Comparison - ${getVersionLabel()}", uninstall: true, install: true) {
        section(title: "<b>Environmental Comfort</b>") {
            input(name: "comfort_method", title: "Comfort System", type: 'enum', options: ['neutral_pvm':'Neutral PVM', 'ashrae55': 'Adaptive ASHRAE-55', 'ashrae55_conditioned': 'Adaptive ASHRAE-55 (Conditioned)', 'en15251': 'Adaptive EN151251','en15251_conditioned': 'Adaptive EN151251 (Conditioned)','utci':'Universal Thermal Climate Index'], required: true, submitOnChange: true)
        }
        if (indoor_configured() && outdoor_configured()) {
            section(title: "<b>Operative Temperature</b>") {
                paragraph "This model uses, or can use, the concept of 'operative temperature'.  While the technical idea of operative temperature can be extremely complicated, the basic implementation is to create an average of the ambient air temperature and the mean radiant temperature, weighted by heat transfer coefficients.  To simplify this calculation it is common to use the temperature of indoor air for ambient temperature and the outdoor temperature as an approximation of mean radiant temperature."  
                input(name: "radiant_adjustment", type: "decimal", title: "Radiant index of your building, between 0 and 1.", required: false, defaultValue: 0.3, submitOnChange: true)
                paragraph "At 0 the outside temperature will not contribute to the calculation, where as at 1 it will be fully averaged into the calculation (i.e. contributing half of the average)."
                paragraph "The current indoor temperature at ${settings.indoorThermostat.displayName} is ${convert_temp_if_needed(indoor_metrics.drybulb)}°${units}, the outdoor temperature is ${convert_temp_if_needed(outdoor_metrics.drybulb)}°${units}, meaning the operative temperature is ${convert_temp_if_needed(t_operative)}°${units} (with a radiant index of ${settings.radiant_adjustment})."
            }
        }
        switch (settings.comfort_method) {
            case 'neutral_pvm':
                section(title: "PVM") {
                    input(name: "coolLow", title: "Low end of comfortable zone when cooling.", type: 'number', required: false)
                    input(name: "coolHigh", title: "High end of comfortable zone when cooling.", type: 'number', required: false)

                    input(name: "coolPmv", title: "PMV in cool mode${settings.coolPmv!=null&&coolConfigured()?' ('+calculateCoolSetpoint()+'°'+unit+')':''}", 
                        type: 'enum', options: coolPmv, required: !settings.heatPmv, submitOnChange: true)
                    if (settings.coolPmv=='custom') {
                        input(name: "coolPmvCustom", title: "Custom cool mode PMV (decimal)", type: 'decimal', range: "-5..*", required: true, submitOnChange: true )
                    }
                    input(name: "coolMet", title: "Metabolic rate", type: 'enum', options: metobolicRates, required: (settings.coolPMV), submitOnChange: true, defaultValue: 1.1 )
                    if (settings.coolMet=='custom') {
                        input(name: "coolMetCustom", title: "Custom cool mode Metabolic rate (decimal)", type: 'decimal', range: "0..*", required: true, submitOnChange: true )
                    }
                    input(name: "coolClo", title: "Clothing level", type: 'enum', options: typicalEnsembles, required: (settings.coolPMV), submitOnChange: true, defaultValue: 0.6 )
                    if (settings.coolClo=='custom') {
                        input(name: "coolCloCustom", title: "Custom cool mode Clothing level (decimal)", type: 'decimal', range: "0..*", required: true, submitOnChange: true )
                    }
                            
                    input(name: "heatLow", title: "Low end of comfortable zone when heating.", type: 'number', required: false)
                    input(name: "heatHigh", title: "High end of comfortable zone when heating.", type: 'number', required: false)
                    
                    input(name: "heatPmv", title: "PMV in heat mode${settings.heatPmv!=null&&heatConfigured()?' ('+calculateHeatSetpoint()+'°'+unit+')':''}", 
                        type: 'enum', options: heatPmv, required: !settings.coolPmv, submitOnChange: true)
                    if (settings.heatPmv=='custom') {
                        input(name: "heatPmvCustom", title: "Custom heat mode PMV (decimal)", type: 'decimal', range: "*..5", required: true, submitOnChange: true )
                    }
                    input(name: "heatMet", title: "Metabolic rate", type: 'enum', options: metobolicRates, required: (settings.heatPMV), submitOnChange: true, defaultValue: 1.1 )
                    if (settings.heatMet=='custom') {
                        input(name: "heatMetCustom", title: "Custom heat mode Metabolic rate (decimal)", type: 'decimal', range: "0..*", required: true, submitOnChange: true )
                    }
                    input(name: "heatClo", title: "Clothing level", type: 'enum', options: typicalEnsembles, required: (settings.heatPMV), submitOnChange: true, defaultValue: 1.0 )
                    if (settings.heatClo=='custom') {
                        input(name: "heatCloCustom", title: "Custom heat mode Clothing level (decimal)", type: 'decimal', range: "0..*", required: true, submitOnChange: true )
                    }
                    paragraph ''



                    def met = settings.coolMet=='custom' ? settings.coolMetCustom : settings.coolMet as BigDecimal
                    def clo = settings.coolClo=='custom' ? settings.coolCloCustom : settings.coolClo as BigDecimal

                    result = ashrae_55_point_pmv_ppd(indoor_metrics.drybulb, t_meanr, humidity, velocity, met, clo)
                    paragraph "The current calculated cooling PMV, using indoor (${convert_temp_if_needed(indoor_metrics.drybulb)}°${units}) and radiant (${convert_temp_if_needed(t_meanr)}°${units}) temperatures is ${result.pmv} (PPD:${result.ppd}).  The neutral temperature according to PMV would be ${getNeutralTempPMV(0)}°${units}."
                    result = ashrae_55_point_pmv_ppd(t_operative, t_operative, humidity, velocity, met, clo)
                    paragraph "The current calculated cooling PMV using operative temperature (${convert_temp_if_needed(t_operative)}°${units}) is ${result.pmv} (PPD:${result.ppd})."

                    met = settings.heatMet=='custom' ? settings.heatMetCustom : settings.heatMet as BigDecimal
                    clo = settings.heatClo=='custom' ? settings.heatCloCustom : settings.heatClo as BigDecimal
                    result = ashrae_55_point_pmv_ppd(indoor_metrics.drybulb, t_meanr, humidity, velocity, met, clo)
                    paragraph "The current calculated heating PMV, using indoor (${convert_temp_if_needed(indoor_metrics.drybulb)}°${units}) and radiant (${convert_temp_if_needed(t_meanr)}°${units}) temperatures is ${result.pmv} (PPD:${result.ppd}).  The neutral temperature according to PMV would be ${getNeutralTempPMV(1)}°${units}."
                    result = ashrae_55_point_pmv_ppd(t_operative, t_operative, humidity, velocity, met, clo)
                    paragraph "The current calculated heating PMV using operative temperature (${convert_temp_if_needed(t_operative)}°${units}) is ${result.pmv} (PPD:${result.ppd})."
                }
                section(title: "General Settings") {
                    input(name: "heatCoolMinDelta", type: "number", title: "Minimum number of degrees allowed between Heating and Cooling setpoints", required: true, defaultValue: 4, submitOnChange: true)
                    paragraph ''
                }
                break;
            case 'utci':
                section(title: "<b>Universal Thermal Climate Index</b>" ) {
                    paragraph "Need to write a description"
                    if (indoor_configured() && outdoor_configured()) {
                        paragraph "UTCI: ${get_UTCI_index()}"
                        paragraph "UTCI bounds: ${get_UTCI_range()}"
                    }
                }
                break;
            case 'ashrae55':
            case 'ashrae55_conditioned':
            case 'en15251':
            case 'en15251_conditioned':
                def strings = adaptive_comfort_string_builder(settings.comfort_method)
                section(title: "<b>${strings.name}</b>" ) {
                    paragraph "${strings.desc}"
                    if (strings.shortname.endsWith('conditioned')) {
                        input(name: "adaptive_condition", type: "decimal", title: "Adaptive conditioning factor", required: false, defaultValue: 0.9, submitOnChange: true)
                        paragraph "A number between 0 and 1 that represents how 'conditioned' vs. 'free-running' the building is. When set to '0' assumption is that the building is passive with no air conditioning; when set to '1' the building is assumed to be fully conditioned (no operable windows and fully air conditioned)."
                    }
                    if (!settings?.adaptive_condition) {
                        settings.adaptive_condition = 0.9
                    }
                }
                if (indoor_configured() && outdoor_configured()) {
                    section(title: "<b>Recent temperature data</b>" ) {
                        paragraph "This algorithm uses a weighted average of outdoor temperatures from the last seven days.  This means either needs to have the data supplied or it will use the current temperature to approximate the results and slowly improve as it gathers more data."
                        def fillFailed = false
                        input(name: "histText", title: "Historical temperature data", type: "text", required: false, submitOnChange: true)
                        paragraph "Enter a list of 168 hourly temperatures for the seven days, separated by commas."
                        input(name: "manualFill", title: "Automatically populate data with current temperature?", type: "bool", defaultValue: false, required: (!settings.histText), submitOnChange: true)
                        paragraph "If you turn this setting on it will create a list using the current outside temperature for all calculations and slowly improve as it records data."
                        def displayError = false
                        if (settings.manualFill)  {
                            def current_t = convert_temp_if_needed(outdoor_metrics.drybulb)
                            log("Populating with temperature ${}","debug")
                            val = [].withEagerDefault() { current_t as BigDecimal }
                            test = val[167]
                            log("Built ${val}","debug")
                            state.temp_history = val
                            app.updateSetting("histText", [type: "string", value: ""])
                            app.updateSetting("manualFill", [type: "bool", value: false])
                        }
                        if (settings.histText && settings.histText != state.temp_history) {
                            tempArray = settings.histText.replace(" ", "").split(',')
                            if (tempArray.length == 168) {
                                state.temp_history = tempArray
                                log("Overwriting temperature data with ${tempArray}.","debug")
                                app.updateSetting("histText", [type: "string", value: ""])
                            } else {
                                displayError = "Invalid historical temperature data entered. Counted ${tempArray.length} entries."
                            }
                        }
                    }
                    section(title: "<b>Calculation factors and Results</b>" ) {
                        if (!displayError) {
                            if (state.temp_history) {
                                paragraph "The current historical temperature data being used is: <br/> ${state.temp_history}"
                                celsius_temps = convert_temp_to_Celsius(state.temp_history)
                                log("Current temperature list (in C) is :${celsius_temps}","trace")
                                tList_p_hourly = weighted_running_mean_hourly(celsius_temps,0.8)
                                log("Weighted running mean hourly results: ${tList_p_hourly}","debug")
                                tList_p_daily = weighted_running_mean_daily(celsius_temps,0.8)
                                log("Weighted running mean daily results: ${tList_p_daily}","debug")
                                t_p = tList_p_hourly[-1]
                                def temp_bounds_message = ''
                                if (t_p < 10 ) {
                                    temp_bounds_message = "<b>This temperature is below the models bounds and will be pinned to ${convert_temp_if_needed(10)}°${units} for calculations.</b>"
                                } else if (t_p > 30) {
                                    temp_bounds_message = "<b>This temperature is above of the models bounds and will be pinned to to ${convert_temp_if_needed(30)}°${units} for calculations.</b>"
                                }
                                paragraph "The most recent outside prevailing temperature is ${convert_temp_if_needed(t_p)}°${units}. ${temp_bounds_message} The current operative temperature being used is ${convert_temp_if_needed(t_operative)}°${units}."
                                def a_s = 0.1
                                paragraph "The current indoor airspeed being used is ${a_s} m/s."
                                switch (strings.shortname) {
                                    case 'ashrae55':
                                        comfort = adaptive_comfort_ashrae55(t_p,t_operative)
                                        cooling = cooling_effect_ashrae55(a_s,t_operative)
                                    break;
                                    case 'ashrae55_conditioned':
                                        comfort = adaptive_comfort_conditioned(t_p,t_operative,settings.adaptive_condition,'ASHRAE-55')
                                        cooling = cooling_effect_ashrae55(a_s,t_operative)
                                    break;
                                    case 'en15251':
                                        comfort = adaptive_comfort_en15251(t_p,t_operative)
                                        cooling = cooling_effect_en15251(a_s,t_operative)
                                    break;
                                    case 'en15251_conditioned':
                                        comfort = adaptive_comfort_conditioned(t_p,t_operative,settings.adaptive_condition,'EN-15251')
                                        cooling = cooling_effect_en15251(a_s,t_operative)
                                    break;
                                    }
                                def cooling_message = "."
                                if (cooling) {
                                    cooling_message = " or ${convert_temp_if_needed(comfort+cooling)}°${units} with a cooling modfier."
                                }
                                paragraph "The estimated neutral temperature using ${strings.name} is ${convert_temp_if_needed(comfort)}°${units}${cooling_message}"
                            }
                        } else {
                            paragraph displayError
                        }
                    }
                }  else {
                    section(title: "<b>Error results</b>" ) {
                        paragraph "${strings.configuration_error}"
                    }
                }
                break;
            }
        }
    }

def adaptive_comfort_string_builder(model) {
    def units = getTemperatureScale()
    def modelInfo = [:]
    switch(model) {
        case 'ashrae55':
            modelInfo.shortname = "ashrae55"
            modelInfo.name = "Adaptive ASHRAE 55"
            modelInfo.desc = "Published by the American Society of Heating, Refrigerating and Air-Conditioning Engineers (ASHRAE), this model is based on the idea that the outdoor climate influences indoor comfort.  The model is based on the assumption that no mechanical cooling (such as fans, blowers, or air conditioning) takes place and that occupants are relatively sedentary and that temperatures will be kept between ${convert_temp_if_needed(10)}°${units} and ${convert_temp_if_needed(33.5)}°${units}."
            modelInfo.configuration_error = "This model requires that both Indoor and Outdoor sections be filled in."
        break;
        case 'ashrae55_conditioned':
            modelInfo.shortname = "ashrae55_conditioned"
            modelInfo.name = "Adaptive ASHRAE 55 (Conditioned)"
            modelInfo.desc = "Based on the ASHRAE 55, this model uses an additional conditioning modifier to account for how much flow occurs between inside and outside air.  Like the ASHRAE 55, model is based on the assumption that no mechanical cooling (such as fans, blowers, or air conditioning) takes place and that occupants are relatively sedentary and that temperatures are between ${convert_temp_if_needed(10)}°${units} and ${convert_temp_if_needed(33.5)}°${units}."
            modelInfo.configuration_error = "This model requires that both Indoor and Outdoor sections be filled in."
        break;
        case 'en15251':
            modelInfo.shortname = "en15251"
            modelInfo.name = "Adaptive EN 151251"
            modelInfo.desc = "Created by the European Standardization Organization (CEN) to support the Energy Performance of Buildings Directive (EPBD) adopted in Europe to align with the Kyoto Protocol, this model is designed to be applied to mixed-mode buildings when mechanical cooling is not operating."
            modelInfo.configuration_error = "This model requires that both Indoor and Outdoor sections be filled in."
        break;
        case 'en15251_conditioned':
            modelInfo.shortname = "en15251_conditioned"
            modelInfo.name = "Adaptive EN 151251 (Conditioned)"
            modelInfo.desc = ""
            modelInfo.configuration_error = "This model requires that both Indoor and Outdoor sections be filled in."
        break;
    }
    return modelInfo
}

def outdoor_configured() {
    if (settings?.outdoor_temp && settings?.outdoor_humidity) {
        if (settings?.outdoor_pressure) {
            if (settings?.pressure_attribute) {
                 return true
            }
        } else {
            return true
        }
    }
    return false
}

def indoor_configured() {
    if (settings?.indoorThermostat && settings?.indoorHumidistat) {
        if (settings.indoorHumidistat.size() > 1) {
            if (settings?.multiHumidRead) {
                return true
            }
        } else {
            return true
        }
    }
    return false
}



void installed() {
    log("Installed with settings: ${settings}",'trace')
    initialize()
}
def uninstalled() {
    log("Uninstalled app","debug")
    deleteAllChildDevices()
}

def deleteAllChildDevices() {
    for (device in getChildDevices())
    {
        deleteChildDevice(device.deviceNetworkId)
    }    
}

void updated() {
    state.version = getVersionLabel()
    log("Updated with settings: ${settings}",'trace')
    unsubscribe()
    unschedule()
    initialize()
}

def initialize() {
    log("${getVersionLabel()} Initializing...", 'info')
    createChildDevices()
    buildSubscriptions()
    state.last = "${app.label} was (re)initialized"
    runIn(2, changeSetpoints, [overwrite: true])
    hourlyTempUpdater()
    runEvery1Hour(hourlyTempUpdater)
    return
}

def buildSubscriptions() {
    log("Building subscriptions","debug")
    if (outdoor_configured()) {
        log("Subscribing to outdoor_temp currentTemperature","debug")
        subscribe(settings.outdoor_temp, 'currentTemperature', outdoorTempChangeHandler)
        log("Subscribing to outdoor_humidity currentHumidity","debug")
        subscribe(settings.outdoor_humidity, 'currentHumidity', outdoorHumidityChangeHandler)
        if (settings?.outdoor_pressure) {
            log("Subscribing to outdoor_pressure ${settings.pressure_attribute}","debug")
            subscribe(settings.outdoor_pressure, settings.pressure_attribute, outdoorPressureChangeHandler)
        } else {
            log("Not subscribing to outdoor_pressure.","debug")
        }
    }
    if (indoor_configured()) {
        subscribe(settings.indoorThermostat, 'currentTemperature', indoorTempChangeHandler)
        subscribe(settings.indoorHumidistat, 'currentHumidity', indoorHumidityChangeHandler)
    }
    //subscribe(location, "sunset", sunsetHandler)
    //subscribe(location, "sunrise", sunriseHandler)
}

def createChildDevices(){
    log("Adding Child Devices if not already added","debug")
    for (i in getSetPoints(settings.comfort_method)) {
        try {
            def name = settings.indoorThermostat.label+" "+i
            log("Trying to create child sensor if it doesn't already exist ${name}","debug")
            def currentchild = getChildDevice(name)
            if (currentchild == null) {
                log("Creating child for ${i}","debug")
                currentchild = addChildDevice("hubitat", "Virtual Temperature Sensor", name, null, [name: "${name}", isComponent: true])
            } else {
                log("Found child for ${name}","debug")
                log("${currentchild}","debug")
            }
        } catch (e) {
            log("Error adding child ${name}: ${e}","debug")
        }
    }
}

boolean configured() {
    return (indoor_configured() && outdoor_configured())
}

boolean coolConfigured() {
    return (configured() &&
            (settings.coolPmv != null && ( settings.coolPmv == 'custom' ? settings.coolPmvCustom != null : true)) &&
            (settings.coolMet != null && ( settings.coolMet == 'custom' ? settings.coolMetCustom != null : true)) &&
            (settings.coolClo != null && ( settings.coolClo == 'custom' ? settings.coolCloCustom != null : true)))
}

boolean heatConfigured() {
    return (configured() &&
            (settings.heatPmv != null && ( settings.heatPmv == 'custom' ? settings.heatPmvCustom != null : true)) &&
            (settings.heatMet != null && ( settings.heatMet == 'custom' ? settings.heatMetCustom != null : true)) &&
            (settings.heatClo != null && ( settings.heatClo == 'custom' ? settings.heatCloCustom != null : true)))
}
        
def hourlyTempUpdater() {
    def outdoor_temp = settings.outdoor_temp.currentTemperature
    def process = true
    if (state.last_history_update == null) {
        state.last_history_update = (new Date()).format("yyyy-MM-dd HH:mm:ss")
    } else {
        use (groovy.time.TimeCategory) {
            def now = (new Date())
            def last = (new Date()).parse("yyyy-MM-dd HH:mm:ss",state.last_history_update)
            duration = TimeCategory.minus(now, last)
            if (duration < 1.hour) {
                process = false
            }
        }
    }
    if (process) {
        state.last_temp_history = state.temp_history
        tempArray = state.temp_history
        tempArray.add(outdoor_temp)
        log("The temp array is ${tempArray}","debug")
        if (tempArray.size > 168) {
            log("Dropping ${tempArray.size - 168} elements.","debug")
            for (int i in 0..(tempArray.size - 169)) {
                log("Dropping measurement ${i} - ${tempArray[0]}","debug")
                tempArray.remove(0)
            }
        }
        state.temp_history = tempArray
        state.last_history_update = (new Date()).format("yyyy-MM-dd HH:mm:ss")
    }
}            


def indoorHumidityChangeHandler(evt) {
    log("Running event handler (Indoor humidity) for event ${evt} (${evt.numberValue}).","debug")
    if (evt.numberValue != null) {
        state.last = evt
        changeSetpoints()
    }
    log("Finished event handler.","debug")
    return null
}

def indoorTempChangeHandler(evt) {
    log("Running event handler (Indoor temp) for event ${evt} (${evt.numberValue}).","debug")
    if (evt.numberValue != null) {
        changeSetpoints()
    }
    log("Finished event handler.","debug")
    return null
}
def outdoorTempChangeHandler(evt) {
    log("Running event handler (outdoor temp) for event ${evt} (${evt.numberValue}).","debug")
    if (evt.numberValue != null) {
        state.last = evt
        changeSetpoints()
    }
    log("Finished event handler.","debug")
    return null
}
def outdoorHumidityChangeHandler(evt) {
    log("Running event handler (outdoor humidity) for event ${evt} (${evt.numberValue}).","debug")
    if (evt.numberValue != null) {
        state.last = evt
        changeSetpoints()
    }
    log("Finished event handler.","debug")
    return null
}
def outdoorPressureChangeHandler(evt) {
    log("Running event handler (outdoor pressure) for event ${evt} (${evt.numberValue}).","debug")
    if (evt.numberValue != null) {
        state.last = evt
        changeSetpoints()
    }
    log("Finished event handler.","debug")
    return null
}

def calculateNeutral(String comfort_method, Boolean useOperativeTemp, BigDecimal adaptive_condition) {
    def comfort = ['cooling': null,
                    'heating': null]
    switch (comfort_method) {
        case 'neutral_pvm':
            comfort.cooling = getNeutralTempPMV(0,useOperativeTemp)
            comfort.heating = getNeutralTempPMV(1,useOperativeTemp)
            break;
        case 'utci':
            comfort.cooling = comfort.heating = get_UTCI_index().utci_approx
            break;
        case 'ashrae55':
        case 'ashrae55_conditioned':
        case 'en15251':
        case 'en15251_conditioned':
            if (!adaptive_condition) {
                adaptive_condition = 0.9
            }
            celsius_temps = convert_temp_to_Celsius(state.temp_history)
            tList_p = weighted_running_mean_hourly(celsius_temps,0.8)
            t_p = tList_p[-1]
            def a_s = 0.1
            switch (comfort_method) {
                case 'ashrae55':
                    comfort.cooling = adaptive_comfort_ashrae55(t_p,t_operative)
                    comfort.heating = comfort.cooling
                    break;
                case 'ashrae55_conditioned':
                    comfort.cooling = adaptive_comfort_conditioned(t_p,t_operative,adaptive_condition,'ASHRAE-55')
                    comfort.heating = comfort.cooling
                    break;
                case 'en15251':
                    comfort.cooling = adaptive_comfort_en15251(t_p,t_operative)
                    comfort.heating = comfort.cooling
                    break;
                case 'en15251_conditioned':
                    comfort.cooling = adaptive_comfort_conditioned(t_p,t_operative,adaptive_condition,'EN-15251')
                    comfort.heating = comfort.cooling
                    break;
            }
            break;
    }
    return comfort
}

void changeSetpoints() {
    def units = getTemperatureScale()
    comfort = calculateNeutral(settings.comfort_method, settings.useOperativeTemp, settings.adaptive_condition)
    state.neutral = comfort
    for (i in getSetPoints(settings.comfort_method)) {
        log("Checking for comfort method ${settings.comfort_method} (working on ${i}).","debug")
        if (i == "Cooling neutral temperature") {
                temp = round(convert_temp_if_needed(comfort.cooling),1)
        } else {
                temp = round(convert_temp_if_needed(comfort.heating),1)
        }
        def name = settings.indoorThermostat.label+" "+i
        def currentchild = false
        try {
            currentchild = getChildDevice(name)
        } catch (e) {
            log("Error finding child ${name}: ${e}","debug")
        }
        if (currentchild) {
            if (currentchild.currentTemperature as BigDecimal != temp as BigDecimal) {
                log("Found child device ${currentchild}, sending event for temperature reading ${temp} not matching ${currentchild.currentTemperature}","debug")
                currentchild.sendEvent(name: "temperature", value: temp, unit: units, isStateChange: true)
            } else {
                log("Found child device ${currentchild}, temperature reading ${temp} matches ${currentchild.currentTemperature}, no need to update.","debug")
            }
        }
    }
}

def round( ArrayList value, decimals=0 ) {
    def sum
    log("Rounding: Arraylist ${value}","trace")
    value.each {sum += it}
    return (sum == null) ? null : round((sum/value.size).toBigDecimal(),decimals)
}

def round( value, decimals=0 ) {
    log("Rounding: ${value}","trace")
    return (value == null) ? null : round(value.toBigDecimal(),decimals)
}
def round( BigDecimal value, decimals=0) {
    log("Rounding: ${value}","trace")
    return (value == null) ? null : value.setScale(decimals, BigDecimal.ROUND_HALF_UP)
}

def getIndoorHumidity() {
    if (!settings.indoorHumidistat) return false
    if (settings.indoorHumidistat.size() == 1)     return settings.indoorHumidistat[0].currentHumidity
    
    def tempList = settings.indoorHumidistat.currentHumidity
    switch(settings.multiHumidRead) {
        case 'average':
            return round( (tempList.sum() / tempList.size()), 0)
            break;
        case 'lowest':
            return tempList.min()
            break;
        case 'highest':
            return tempList.max()
            break;
    }
}

def getOutdoorHumidity() {
    if (!settings.outdoor_humidity) return false
    return settings.outdoor_humidity.currentHumidity
}

/*                    
def getMultiThermometers() {
    if (!settings.thermometers)             return settings.indoorThermostat.currentTemperature
    if (settings.thermometers.size() == 1)     return settings.thermostats[0].currentTemperature
    
    def tempList = settings.thermometers.currentTemperature
    def result
    switch(settings.multiTempType) {
        case 'average':
            return round( (tempList.sum() / tempList.size()), (getTemperatureScale()=='C'?2:1))
            break;
        case 'lowest':
            return tempList.min()
            break;
        case 'highest':
            return tempList.max()
            break;
    }
}
*/

// Helper Functions
                    
private determineLogLevel(data) {
    switch (data?.toUpperCase()) {
        case "TRACE":
            return 0
            break
        case "DEBUG":
            return 1
            break
        case "INFO":
            return 2
            break
        case "WARN":
            return 3
            break
        case "ERROR":
        	return 4
            break
        default:
            return 1
    }
}

public log(data, type) {
    data = "${appName()} -- ${data ?: ''}"
    if (determineLogLevel(type) >= determineLogLevel(settings.logging ?: "INFO")) {
        switch (type?.toUpperCase()) {
            case "TRACE":
                log.trace "${data}"
                break
            case "DEBUG":
                log.debug "${data}"
                break
            case "INFO":
                log.info "${data}"
                break
            case "WARN":
                log.warn "${data}"
                break
            case "ERROR":
                log.error "${data}"
                break
            default:
                log.error "${appName()} -- ${device.label} -- Invalid Log Setting"
        }
    }
}

def get_sensorpressure() {
    if (settings.pressure_sensor_type == 'none') {
        app.removeSetting('outdoor_pressure')
        app.removeSetting('pressure_attribute')
    }
    log("pressure_sensor_type: ${settings.pressure_sensor_type}","debug")
    log("outdoor_pressure: ${settings?.outdoor_pressure}","debug")
    log("pressure_attribute: ${settings?.pressure_attribute}","debug")
    if (settings.pressure_sensor_type != 'none' && settings?.outdoor_pressure && settings?.pressure_attribute) {
       return settings.outdoor_pressure.currentValue("${settings.pressure_attribute}")*100
    }
    return false
}

def get_pressure() {
    def atmosPress = 101325.0 //magic numbers for the win - this is normal pressure at sea level [Pa]
    def method = 'assumed'
    atmosPress_test = get_sensorpressure()
    if (atmosPress_test) {
        atmosPress = settings.outdoor_pressure.currentValue("${settings.pressure_attribute}")*100
        method = 'measured'
    } else { 
        if (settings?.elevation) {
            atmosPress = atmosPress * Math.pow((1-2.25577 * (Math.pow(10,-5)) * (settings.elevation as BigDecimal)),5.25588)
            method = 'calculated'
        }
    }
    return ['atmos': atmosPress, 'method': method]
}

def atmospheric_pressure_units() {
    def unitsList = [ "inHg", "mbar", "hPa", "kPa", "lbf/ft²", "at" ]
    return unitsList
}

def convert_pressure_units(value, units) {
    def unitsList = [ "inHg", "mbar", "hPa", "kPa", "lbf/ft²", "at" ]
    return unitsList
}

def getPsycoMetricsLocalTemp(Boolean outdoor = true) {
    metrics = getPsycoMetrics(outdoor)
    metrics.drybulb = convert_temp_if_needed(metrics.drybulb)
    metrics.wetbulb = convert_temp_if_needed(metrics.wetbulb)
    metrics.dewpoint = convert_temp_if_needed(metrics.dewpoint)
    if (!outdoor) {
        metrics.iWBGT = convert_temp_if_needed(metrics.iWBGT)
    }
    newMetrics = [:]
    metrics.each{ key,value ->
        if (key != "humRatio") {
            newMetrics."${key}" = round(value,1)
        } else {
            newMetrics."${key}" = round(value,6)
        }
    }
    log("metrics: ${newMetrics}","debug")
    return newMetrics
}

def getPsycoMetrics(Boolean outdoor = true) {
    def atmosPress = (get_pressure()).atmos
    def temperature = convert_temp_to_Celsius(settings.outdoor_temp.currentTemperature)
    def humidity = getOutdoorHumidity()

    def vel = 0.1
    if (!outdoor) {
        temperature = convert_temp_to_Celsius(settings.indoorThermostat.currentTemperature)
        humidity = getIndoorHumidity()
    }
    metrics = psychometrics_from_drybulb_rel_humidity(temperature,humidity,atmosPress)
    if (!outdoor) {
        metrics.iWBGT = indoor_WBGT(metrics.drybulb,metrics.dewpoint) 
        metrics.velocity = vel
    }
    metrics.pressure = atmosPress
    log("metrics: ${metrics}","debug")
    return metrics
}

def get_UTCI_range() {
    def metrics = getPsycoMetrics(false)
    def velocity = metrics.velocity
    def temperature = metrics.drybulb
    def t_meanr = metrics.iWBGT
    def satPress = metrics.satPress
    def humidity = metrics.relHum  
    def vapPress = metrics.vapPress    
    def tsat_l = 0
    def tsat_r = 30
    def eps = 0.001
    def fn = { t ->
        result = universal_thermal_climate_index(t,t_meanr,velocity,vapPress).utci_approx
        if (result != "None") {
            9.0 - result
        } else {
            0
        }
    }
    low_temp = bisect(tsat_l, tsat_r, fn, eps, 0)
    tsat_l = 0
    tsat_r = 40
    eps = 0.001
    fn = { t ->
        result = universal_thermal_climate_index(t,t_meanr,velocity,vapPress).utci_approx
        if (result != "None") {
            26.0 - result
        } else {
            0
        }
    }
    high_temp = bisect(tsat_l, tsat_r, fn, eps, 0)
    return ['lower_utci':convert_temp_if_needed(low_temp),'upper_utci':convert_temp_if_needed(high_temp)]
}


def get_UTCI_index() {
    def metrics = getPsycoMetrics(false)
    def velocity = metrics.velocity
    def temperature = metrics.drybulb
    def t_meanr = metrics.iWBGT
    def satPress = metrics.satPress
    def humidity = metrics.relHum  
    def vapPress = metrics.vapPress    
    def results = universal_thermal_climate_index(temperature,t_meanr,velocity,vapPress)
    if (results.utci_approx != "None") {
        results.utci_approx = convert_temp_if_needed(results.utci_approx)
        return results
    } else {
        return false
    }
}

def calculateCoolSetpoint() {
    return getNeutralTempPMV(0,true)
}

def calculateHeatSetpoint() {
    return getNeutralTempPMV(1,true)
}

def getNeutralTempPMV(season=0,Boolean operative = false) {
    //season: 0=cooling season (summer) 1=heating season (Winter) 

    def indoor_metrics = getPsycoMetrics(false)
    // gather t_air, t_meanr,  rel_h, vel=0.1, metabolic, clothing
    def velocity = indoor_metrics.velocity
    def temperature = indoor_metrics.drybulb
    def t_meanr = indoor_metrics.iWBGT
    def satPress = indoor_metrics.satPress
    def humidity = indoor_metrics.relHum  
    def vapPress = indoor_metrics.vapPress
    if (operative) {
        def outdoor_metrics = getPsycoMetrics(true)
        temperature = calc_operative_temp(indoor_metrics.drybulb,outdoor_metrics.drybulb,settings.radiant_adjustment)
        t_meanr = temperature
    }

    def met = settings.coolMet=='custom' ? settings.coolMetCustom : settings.coolMet as BigDecimal
    def clo = settings.coolClo=='custom' ? settings.coolCloCustom : settings.coolClo as BigDecimal
    if (season == 1) {
        met = settings.heatMet=='custom' ? settings.heatMetCustom : settings.heatMet as BigDecimal
        clo = settings.heatClo=='custom' ? settings.heatCloCustom : settings.heatClo as BigDecimal
    }
    def tsat_l = -50
    def tsat_r = 50
    def eps = 0.001
    def fn = { t ->
        0.0 - (ashrae_55_point_pmv_ppd(t, t_meanr, humidity, velocity, met, clo).pmv)
        }
    neutral_temp = bisect(tsat_l, tsat_r, fn, eps, 0);
    log("Neutral temp = ${neutral_temp}","debug")
    return round(convert_temp_if_needed(neutral_temp),2)
}

 def bisect(a, b, fn, epsilon, target) {
     //use bisection to solve polynomial passed as fn
    def a_T
    def b_T
    def midpoint
    def midpoint_T

    while (Math.abs(b - a) > 2 * epsilon) {
        midpoint = (b + a) / 2
        a_T = fn.call(a)
        b_T = fn.call(b)
        midpoint_T = fn.call(midpoint)
        log("${a} (${a_T}), ${b} (${b_T}), midpoint ${midpoint} (${midpoint_T})","trace")
        if ((a_T - target) * (midpoint_T - target) < 0) b = midpoint
        else if ((b_T - target) * (midpoint_T - target) < 0) a = midpoint
        else return null
    }
    return midpoint;
}


def psychometrics_from_drybulb_rel_humidity(t_db, rel_h, atmos_p) {
    def metrics = [:]
    metrics.satPress = saturation_pressure(t_db)
    metrics.relHum = rel_h as Float
    metrics.vapPress = rel_h / 100 * metrics.satPress
    metrics.humRatio = humratio(atmos_p, metrics.vapPress)
    metrics.drybulb = t_db
    metrics.wetbulb = wetbulb(t_db, metrics.humRatio,atmos_p)
    metrics.dewpoint = dewpoint(metrics.humRatio,atmos_p)
    return metrics
}

def humratio(atmos_pressure, partial_water) {
    //Calculate the humidity ratio as a function of partial water vapor pressure
    return 0.62198 * partial_water / (atmos_pressure - partial_water)
}

def dewpoint(hum_ratio, atmos_pressure) {
    //Calculate dewpoint as saturation temperature at water vapor partial pressure
    //hum_ratio: humidity ratio
    //atmos_pressure: atmospheric pressure [Pa]
    def partialWater = (atmos_pressure * hum_ratio) / (0.62198 + hum_ratio);
    return saturation_temp(partialWater);
}

def saturation_pressure(t_db) {
    //t_db: dry bulb temperature [°C]
    
    //convert to Kelvin
    def tKel = t_db + 273.15
    def C1 = -5674.5359
    def C2 = 6.3925247
    def C3 = -0.9677843 * (10 ** -2)
    def C4 = 0.62215701 * (10 ** -6)
    def C5 = 0.20747825 * (10 ** -8)
    def C6 = -0.9484024 * (10 ** -12)
    def C7 = 4.1635019
    def C8 = -5800.2206
    def C9 = 1.3914993
    def C10 = -0.048640239
    def C11 = 0.41764768 * (10 ** -4)
    def C12 = -0.14452093 * (10 ** -7)
    def C13 = 6.5459673
    def pascals = null

    if (tKel < 273.15) {
        pascals = Math.exp(C1 / tKel + C2 + tKel * (C3 + tKel * (C4 + tKel * (C5 + C6 * tKel))) + C7 * Math.log(tKel));
    } else if (tKel >= 273.15) {
        pascals = Math.exp(C8 / tKel + C9 + tKel * (C10 + tKel * (C11 + tKel * C12)) + C13 * Math.log(tKel));
    }
    return pascals
}

def saturation_temp(pressure) {
    //Calculate saturation (boiling) temperature of water given pressure
    def w = pressure as BigDecimal
    def tsat_l = -50
    def tsat_r = 500
    def eps = 0.00015
    def fn = { t ->
        w - saturation_pressure(t) 
        }
    return bisect(tsat_l, tsat_r, fn, eps, 0);
}

def wetbulb(t_db, hum_ratio, atmos_pressure) {
    //Calculate wet bulb temperature given drybulb temp, humidity ration and pressure
    def tdb = t_db as BigDecimal
    def w = hum_ratio as BigDecimal
    def Patm = atmos_pressure
    def eps = 0.01
    def wetbulb_l = -100
    def wetbulb_r = 200

    def fn = { t ->
        def CpAir = 1004.0
        def CpWat = 4186.0
        def CpVap = 1805.0
        def Hfg = 2501000.0
        def RAir = 287.055
        def TKelConv = 273.15
        psatStar = saturation_pressure(t);
        wStar = humratio(Patm, psatStar);
        newW = ((Hfg - CpWat - CpVap * t) * wStar - CpAir * (tdb - t)) / (Hfg + CpVap * tdb - CpWat * t);
        return (w - newW);
    }
    return bisect(wetbulb_l, wetbulb_r, fn, eps, 0);
}

def indoor_WBGT(t_air,t_dew) {
    //Calculate an estimate of indoor WBGT using T_air and dewpoint
    //assuming no substantial radiation sources
    //equation 8 and 9 from https://www.jstage.jst.go.jp/article/indhealth/50/4/50_MS1352/_pdf

    def t_WBGT = t_dew + 0.2 //initial starting point
    def e_d = 0.6106 * Math.exp(17.27 * t_dew / (237.7 + t_dew))
    def e_w = 0.6106 * Math.exp(17.27 * t_WBGT / (237.7 + t_WBGT))

    def mcph_1 = (e_d - e_w) * (1556 - 1.484 * t_WBGT) + 101 * (t_air - t_WBGT)
    def mcph_2 = (e_d - e_w) * (1556 - 1.484 * t_WBGT) + 101 * (t_air - t_WBGT)

    while (t_WBGT <= t_air && ((mcph_1 > 0 && mcph_2 > 0) || (mcph_1 < 0 && mcph_2 <0))) {
        e_w = 0.6106 * Math.exp(17.27 * t_WBGT / (237.7 + t_WBGT))
        mcph_1 = mcph_2
        mcph_2 = (e_d - e_w) * (1556 - 1.484 * t_WBGT) + 1010 * (t_air - t_WBGT)
        t_WBGT += 0.2
        log("WBGT: ${t_WBGT}, mcph_1=${mcph_1}, mcph_2=${mcph_2}","trace")
    }
    return t_WBGT as BigDecimal
}


def ashrae_55_point_pmv_ppd(t_air, t_meanr, rel_h, vel=0.1, metabolic=1.0, clothing=0.5) {
    // t_air, air temperature [°C]
    // t_meanr, radiant mean temperature [°C]
    // rel_h, relative humidity [%]
    // vel, air speed/velocity [m/s]
    // metabolic, metabolic rate (calculated index)
    // clothing, thermal insulation (calculated index)
    log("Running ashrae_55_point_pmv_ppd with inputs ${t_air}, ${t_meanr}, ${rel_h}, ${vel}, ${metabolic}, ${clothing} ","debug")

    def hcn, hc, pmv, n

    def pa = rel_h * 10 * Math.exp(16.6536 - 4030.183 / (t_air + 235))
    if (clothing == null) {
        clothing = 0.5
    }
    if (metabolic == null) {
        metabolic = 1.0
    }
    def icl = 0.155 * clothing //thermal insulation of the clothing in SI  [m^2K/W]
    
    def fcl = 1.05 + (0.645 * icl)
    if (icl <= 0.078) fcl = 1 + (1.29 * icl)
 
    def m = metabolic * 58.15 //metabolic rate in SI [W/m^2]

    //heat transfer coefficient by forced convection
    def hcf = 12.1 * Math.sqrt(vel)

    // Calculate absolute air & mean radiant temperature
    def taa = t_air + 273
    def tra = t_meanr + 273

    // Calculate surface temperature of clothing by iteration
    def tcla = taa + (35.5 - t_air) / (3.5 * (icl + 0.1))

    def p1 = icl * fcl
    def p2 = p1 * 3.96
    def p3 = p1 * 100
    def p4 = p1 * taa
    def p5 = 308.7 - 0.028 * m + p2 * Math.pow(tra / 100, 4)
    def xn = tcla / 100
    def xf = tcla / 50
    def eps = 0.00015

    n = 0
    while ((Math.abs(xn - xf) > eps) && (n < 150)) {
        xf = (xf + xn) / 2
        // Heat transfer coefficient by natural convection
        hcn = 2.38 * Math.pow(Math.abs(100.0 * xf - taa), 0.25)
        hc = hcn
        if (hcf > hcn) hc = hcf 
        xn = (p5 + p4 * hc - p2 * Math.pow(xf, 4)) / (100 + p3 * hc)
        ++n
    }

    // Calculate surface temperature of clothing
    tcl = 100 * xn - 273
 
    def hl1 = 3.05 * 0.001 * (5733 - (6.99 * m) - pa) // heat loss diff. through skin
    def hl2 = 0
    if (m > 58.15) hl2 = 0.42 * (m - 58.15)// heat loss by sweating
    def hl3 = 1.7 * 0.00001 * m * (5867 - pa) // latent respiration heat loss
    def hl4 = 0.0014 * m * (34 - t_air) // dry respiration heat loss
    def hl5 = 3.96 * fcl * (Math.pow(xn, 4) - Math.pow (tra / 100, 4)) // heat loss by radiation
    def hl6 = fcl * hc * (tcl - t_air) // heat loss by convection
    def ts = 0.303 * Math.exp(-0.036 * m) + 0.028 // Thermal sensation transfer coefficient
    pmv = ts * (m - hl1 - hl2 - hl3 - hl4 - hl5 - hl6) // Predicted mean vote
    log("PMV found to be ${round(pmv,2)} at temperature ${round(t_air,2)} with radiant temp of ${round(t_meanr,2)} and relative humidity of ${round(rel_h,2)}.","debug")
    ppd = 100.0 - 95.0 * Math.exp(-0.03353 * Math.pow(pmv, 4.0) - 0.2179 * Math.pow(pmv, 2.0));
    return ["pmv": pmv, "ppd": ppd]
}

def adaptive_comfort_ashrae55(t_prevail, t_o) {
    //t_prevail: The prevailing outdoor temperature [°C].  For the ASHRAE-55 adaptive comfort model, this is typically the average monthly outdoor temperature.
    //t_o: Operative temperature [°C]
    if (t_prevail < 10)  {
        t_prevail = 10
    }
    else if (t_prevail > 33.5)  {
        t_prevail = 33.5
    }
    t_comf = neutral_temperature_ashrae55(t_prevail)
    return t_comf
}

def adaptive_comfort_en15251(t_prevail, t_o) {
    //t_prevail: The prevailing outdoor temperature [°C].  For the ASHRAE-55 adaptive comfort model, this is typically the average monthly outdoor temperature.
    //t_o: Operative temperature [°C]
    if (t_prevail < 10) t_prevail = 10
    if (t_prevail > 30) t_prevail = 30
    t_comf = neutral_temperature_en15251(t_prevail)
    return t_comf
}

def adaptive_comfort_conditioned(t_prevail, t_o, conditioning = 0.9, model) {
    //t_prevail: The prevailing outdoor temperature [°C].  For the ASHRAE-55 adaptive comfort model, this is typically the average monthly outdoor temperature.
    //t_o: Operative temperature [°C]
    //conditioning: A number between 0 and 1 that represents how "conditioned" vs. "free-running" the building is.
    //    0 = free-running (completely passive with no air conditioning)
    //    1 = conditioned (no operable windows and fully air conditioned)
    //model: The comfort standard, which will be used to represent the "free-running" function.  Chose from: 'EN-15251', 'ASHRAE-55'.
    if (t_prevail < 10) t_prevail = 10
    if (t_prevail > 30) t_prevail = 30
    t_comf = neutral_temperature_conditioned(t_prevail, conditioning, model)
    return t_comf
}

def calc_operative_temp(t_air, t_rad, r_index = 0.5) {
    //t_air: Air temperature [°C]
    //t_rad: Mean radiant temperature [°C]
    if (r_index > 1) r_index = 1
    if (r_index < 0) r_index = 0
    r_index = r_index/2
    return ((t_air*(1-r_index)) + (t_rad*r_index))
}

def neutral_temperature_ashrae55(t_prevail) {
    return 0.31 * t_prevail + 17.8
}

def neutral_temperature_en15251(t_prevail) {
    return 0.33 * t_prevail + 18.8
}

def neutral_temperature_conditioned(t_prevail, conditioning, model='EN-15251') {
    if (conditioning == 1) {
        t_comf = 0.09 * t_prevail + 22.6
    } else if (model == 'ASHRAE-55') {
        inv_conditioning = 1 - conditioning
        t_comf = ((0.09 * conditioning) + (0.31 * inv_conditioning)) * t_prevail + ((22.6 * conditioning) + (17.8 * inv_conditioning))
    } else if (model == 'EN-15251') {
        inv_conditioning = 1 - conditioning
        t_comf = ((0.09 * conditioning) + (0.33 * inv_conditioning)) * t_prevail + ((22.6 * conditioning) + (18.8 * inv_conditioning))
    } else {
        log("Model ${model} not recognized: Use EN-15251 or ASHRAE-55","debug")
    }
    return t_comf
}

def cooling_effect_ashrae55(vel, t_o) {
    //vel: Relative air velocity [m/s]
    //t_o : Operative Temperature [°C]
    ce = 0
    if (vel >= 0.6 && t_o >= 25.0) {
        if (vel < 0.9)  {
            ce = 1.2
        } else if (vel < 1.2) {
            ce = 1.8
        } else if (vel >= 1.2) {
            ce = 2.2
        }
    }
    return ce
}

def cooling_effect_en15251(vel, t_o) {
    //vel: Relative air velocity [m/s]
    //t_o : Operative Temperature [°C]
    ce = 0
    if (vel >= 0.2 && t_o >= 25)
        ce = 1.7856 * Math.log(vel) + 2.9835
    return ce
}

def ashrae55_neutral_offset_from_ppd(ppd=90) {
    //ppd: The acceptable Percentage of People Dissatisfied (PPD). Usually, 80% or 90%
    if (ppd < 100 && ppd > 0)  {
        return -0.1 * ppd + 11.5
    }
    return 0
}

def en15251_neutral_offset_from_comfort_class(comf_class) {
    if (comf_class in 1..3) return comf_class+1
    return 0
}

def convert_temp_to_Celsius(List temp_list) {
    def units = getTemperatureScale()
    if (units == 'C') { return temp_list}
    return temp_list.collect { fahrenheitToCelsius(it as BigDecimal) } 
}

def convert_temp_to_Celsius(String[] temp_list) {
    def units = getTemperatureScale()
    if (units == 'C') { return temp_list}
    return temp_list.collect { fahrenheitToCelsius(it as BigDecimal) } 
}

def convert_temp_to_Celsius(BigDecimal temp) {
    def units = getTemperatureScale()
    if (units == 'C') { return temp}
    return fahrenheitToCelsius(temp as BigDecimal) 
}

def convert_temp_to_Celsius(String temp) {
    def units = getTemperatureScale()
    if (units == 'C') { return temp as BigDecimal}
    return fahrenheitToCelsius(temp as BigDecimal) 
} 

def convert_temp_if_needed(String temp_list) {
    return convertTemperatureIfNeeded(temp_list as BigDecimal,"C",2) 
}

def convert_temp_if_needed(BigDecimal temp_list) {
    return convertTemperatureIfNeeded(temp_list as BigDecimal,"C",2) 
}

def convert_temp_if_needed(List temp_list) {
    return temp_list.collect { convertTemperatureIfNeeded(it as BigDecimal,"C",2) } 
}

def convert_temp_if_needed(String[] temp_list) {
    return temp_list.collect { convertTemperatureIfNeeded(it as BigDecimal,"C",2) } 
}

def weighted_running_mean_hourly(outdoor_temperatures, alpha=0.8) {
    //outdoor_temperatures: list of 168 hourly outdoor temperatures in Celcius
    //alpha: A constant between 0 and 1 that governs how quickly the running mean responds to the outdoor temperature.
    if (outdoor_temperatures.size != 168) {
        log("Have ${outdoor_temperatures.size} temperature measurements. Exiting.","debug")
        return null
    }
    log("Outdoor temps: ${outdoor_temperatures}","debug")

    divisor = 1 + alpha + alpha ** 2 + alpha ** 3 + alpha ** 4 + alpha ** 5
    dividend = ((outdoor_temperatures[-24..-1]).sum() / 24) + \
        (alpha * (outdoor_temperatures[-48..-25]).sum() / 24) + \
        (alpha ** 2 * (outdoor_temperatures[-72..-49]).sum() / 24) + \
        (alpha ** 3 * (outdoor_temperatures[-96..-73]).sum() / 24) + \
        (alpha ** 4 * (outdoor_temperatures[-120..-97]).sum() / 24) + \
        (alpha ** 5 * (outdoor_temperatures[-144..-121]).sum() / 24)

    //compute the initial prevailing outdoor temperature by looking over the past week
    starting_temp = dividend / divisor
    daily_run_means = [starting_temp]
    daily_means = [(outdoor_temperatures[0..23].sum()) / 24]
    prevailing_temp = [starting_temp] * 24

   //run through each day of data and compute the running mean using the previous day's
    start_hour = 24
    for (i in 1..(Math.floor(outdoor_temperatures.size / 24) - 1)) {
        daily_mean = (outdoor_temperatures[start_hour..(start_hour + 23)]).sum() / 24
        daily_run_mean = ((1 - alpha) * daily_means[-1]) + alpha * daily_run_means[-1]
        daily_run_means = daily_run_means + daily_run_mean
        daily_means = daily_means + daily_mean
        prevailing_temp= prevailing_temp + ([daily_run_mean]*24)
        start_hour += 24
    }
    log("Prevailing hourly temps: ${prevailing_temp}","debug")
    return prevailing_temp
}

def weighted_running_mean_daily(outdoor_temperatures, alpha=0.8){
    //outdoor_temperatures: list of 7 daily outdoor temperatures in Celcius
    //alpha: A constant between 0 and 1 that governs how quickly the running mean responds to the outdoor temperature.
    start_hour = 24
    def daily_mean = []
    for (i in 0..(Math.floor(outdoor_temperatures.size / 24) - 1)) {
            daily_mean.add((outdoor_temperatures[start_hour..(start_hour + 23)]).sum() / 24)
    }
    if (daily_mean.size != 7) {
        log("Only have ${daily_mean.size} temperature measurements. Exiting.","debug")
        return null
    }
    log("Outdoor temps: ${daily_mean}","debug")

    divisor = 1 + alpha + alpha ** 2 + alpha ** 3 + alpha ** 4 + alpha ** 5

    dividend = daily_mean[-1] + alpha * daily_mean[-2] + \
        alpha ** 2 * daily_mean[-3] + alpha ** 3 * daily_mean[-4] + \
        alpha ** 4 * daily_mean[-5] + alpha ** 5 * daily_mean[-6]

    //compute the initial prevailing outdoor temperature by looking over the past week
    starting_temp = dividend / divisor
    daily_run_means = [starting_temp]
    daily_means = [daily_mean[0]]
    //run through each day of data and compute the running mean using the previous day's
    
    for (i in 1..(daily_mean.size - 1)) {
        daily_run_mean = ((1 - alpha) * daily_means[-1]) + alpha * daily_run_means[-1]
        daily_run_means.add(daily_run_mean)
        daily_means.add(daily_mean[i])
    }
    log("${prevailing_temp}","debug")
    return prevailing_temp
}

def check_prevailing_temperatures_ashrae55(t_prevail) {
    return check_prevailing_temperatures_range(t_prevail, 10.0, 33.5)
}


def check_prevailing_temperatures_en15251(t_prevail) {
    return check_prevailing_temperatures_range(t_prevail, 10.0, 30.0)
}

def check_prevailing_temperatures_range(t_prevail, lower, upper) {
    return (t_prevail.every { lower <= it }) && (t_prevail.every { it <= upper })? true : false
}

def universal_thermal_climate_index(t_a, t_r, vel, v_p){
    //t_a: Air temperature [°C]
    //t_r: Mean radiant temperature [°C]
    //vel: Wind speed 10 m above ground level [m/s].
    //rh: Relative humidity [%]

    check = ((t_a < -50.0 || t_a > 50.0) || (t_r - t_a < -30.0 || t_r - t_a > 70.0))
    log("running universal_thermal_climate_index with ${t_a}, ${t_r},  ${vel},  ${v_p}","trace")
    vel = (vel < 0.5) ? 0.5 : vel
    vel = (vel > 17) ? 17 : vel

    //This is a groovy version of the UTCI_approx function
    //Version a 0.002, October 2009
    def comfortable = false
    def stressRange = 2
    def UTCI_approx = null
    if (!check) {
        D_t_r = t_r - t_a
        //convert vapour pressure to kPa
        v_p = v_p / 10
        
        UTCI_approx = (t_a +
            (0.607562052) +
            (-0.0227712343) * t_a +
            (8.06470249 * (10 ** (-4))) * t_a * t_a +
            (-1.54271372 * (10 ** (-4))) * t_a * t_a * t_a +
            (-3.24651735 * (10 ** (-6))) * t_a * t_a * t_a * t_a +
            (7.32602852 * (10 ** (-8))) * t_a * t_a * t_a * t_a * t_a +
            (1.35959073 * (10 ** (-9))) * t_a * t_a * t_a * t_a * t_a * t_a +
            (-2.25836520) * vel +
            (0.0880326035) * t_a * vel +
            (0.00216844454) * t_a * t_a * vel +
            (-1.53347087 * (10 ** (-5))) * t_a * t_a * t_a * vel +
            (-5.72983704 * (10 ** (-7))) * t_a * t_a * t_a * t_a * vel +
            (-2.55090145 * (10 ** (-9))) * t_a * t_a * t_a * t_a * t_a * vel +
            (-0.751269505) * vel * vel +
            (-0.00408350271) * t_a * vel * vel +
            (-5.21670675 * (10 ** (-5))) * t_a * t_a * vel * vel +
            (1.94544667 * (10 ** (-6))) * t_a * t_a * t_a * vel * vel +
            (1.14099531 * (10 ** (-8))) * t_a * t_a * t_a * t_a * vel * vel +
            (0.158137256) * vel * vel * vel +
            (-6.57263143 * (10 ** (-5))) * t_a * vel * vel * vel +
            (2.22697524 * (10 ** (-7))) * t_a * t_a * vel * vel * vel +
            (-4.16117031 * (10 ** (-8))) * t_a * t_a * t_a * vel * vel * vel +
            (-0.0127762753) * vel * vel * vel * vel +
            (9.66891875 * (10 ** (-6))) * t_a * vel * vel * vel * vel +
            (2.52785852 * (10 ** (-9))) * t_a * t_a * vel * vel * vel * vel +
            (4.56306672 * (10 ** (-4))) * vel * vel * vel * vel * vel +
            (-1.74202546 * (10 ** (-7))) * t_a * vel * vel * vel * vel * vel +
            (-5.91491269 * (10 ** (-6))) * vel * vel * vel * vel * vel * vel +
            (0.398374029) * D_t_r +
            (1.83945314 * (10 ** (-4))) * t_a * D_t_r +
            (-1.73754510 * (10 ** (-4))) * t_a * t_a * D_t_r +
            (-7.60781159 * (10 ** (-7))) * t_a * t_a * t_a * D_t_r +
            (3.77830287 * (10 ** (-8))) * t_a * t_a * t_a * t_a * D_t_r +
            (5.43079673 * (10 ** (-10))) * t_a * t_a * t_a * t_a * t_a * D_t_r +
            (-0.0200518269) * vel * D_t_r +
            (8.92859837 * (10 ** (-4))) * t_a * vel * D_t_r +
            (3.45433048 * (10 ** (-6))) * t_a * t_a * vel * D_t_r +
            (-3.77925774 * (10 ** (-7))) * t_a * t_a * t_a * vel * D_t_r +
            (-1.69699377 * (10 ** (-9))) * t_a * t_a * t_a * t_a * vel * D_t_r +
            (1.69992415 * (10 ** (-4))) * vel * vel * D_t_r +
            (-4.99204314 * (10 ** (-5))) * t_a * vel * vel * D_t_r +
            (2.47417178 * (10 ** (-7))) * t_a * t_a * vel * vel * D_t_r +
            (1.07596466 * (10 ** (-8))) * t_a * t_a * t_a * vel * vel * D_t_r +
            (8.49242932 * (10 ** (-5))) * vel * vel * vel * D_t_r +
            (1.35191328 * (10 ** (-6))) * t_a * vel * vel * vel * D_t_r +
            (-6.21531254 * (10 ** (-9))) * t_a * t_a * vel * vel * vel * D_t_r +
            (-4.99410301 * (10 ** (-6))) * vel * vel * vel * vel * D_t_r +
            (-1.89489258 * (10 ** (-8))) * t_a * vel * vel * vel * vel * D_t_r +
            (8.15300114 * (10 ** (-8))) * vel * vel * vel * vel * vel * D_t_r +
            (7.55043090 * (10 ** (-4))) * D_t_r * D_t_r +
            (-5.65095215 * (10 ** (-5))) * t_a * D_t_r * D_t_r +
            (-4.52166564 * (10 ** (-7))) * t_a * t_a * D_t_r * D_t_r +
            (2.46688878 * (10 ** (-8))) * t_a * t_a * t_a * D_t_r * D_t_r +
            (2.42674348 * (10 ** (-10))) * t_a * t_a * t_a * t_a * D_t_r * D_t_r +
            (1.54547250 * (10 ** (-4))) * vel * D_t_r * D_t_r +
            (5.24110970 * (10 ** (-6))) * t_a * vel * D_t_r * D_t_r +
            (-8.75874982 * (10 ** (-8))) * t_a * t_a * vel * D_t_r * D_t_r +
            (-1.50743064 * (10 ** (-9))) * t_a * t_a * t_a * vel * D_t_r * D_t_r +
            (-1.56236307 * (10 ** (-5))) * vel * vel * D_t_r * D_t_r +
            (-1.33895614 * (10 ** (-7))) * t_a * vel * vel * D_t_r * D_t_r +
            (2.49709824 * (10 ** (-9))) * t_a * t_a * vel * vel * D_t_r * D_t_r +
            (6.51711721 * (10 ** (-7))) * vel * vel * vel * D_t_r * D_t_r +
            (1.94960053 * (10 ** (-9))) * t_a * vel * vel * vel * D_t_r * D_t_r +
            (-1.00361113 * (10 ** (-8))) * vel * vel * vel * vel * D_t_r * D_t_r +
            (-1.21206673 * (10 ** (-5))) * D_t_r * D_t_r * D_t_r +
            (-2.18203660 * (10 ** (-7))) * t_a * D_t_r * D_t_r * D_t_r +
            (7.51269482 * (10 ** (-9))) * t_a * t_a * D_t_r * D_t_r * D_t_r +
            (9.79063848 * (10 ** (-11))) * t_a * t_a * t_a * D_t_r * D_t_r * D_t_r +
            (1.25006734 * (10 ** (-6))) * vel * D_t_r * D_t_r * D_t_r +
            (-1.81584736 * (10 ** (-9))) * t_a * vel * D_t_r * D_t_r * D_t_r +
            (-3.52197671 * (10 ** (-10))) * t_a * t_a * vel * D_t_r * D_t_r * D_t_r +
            (-3.36514630 * (10 ** (-8))) * vel * vel * D_t_r * D_t_r * D_t_r +
            (1.35908359 * (10 ** (-10))) * t_a * vel * vel * D_t_r * D_t_r * D_t_r +
            (4.17032620 * (10 ** (-10))) * vel * vel * vel * D_t_r * D_t_r * D_t_r +
            (-1.30369025 * (10 ** (-9))) * D_t_r * D_t_r * D_t_r * D_t_r +
            (4.13908461 * (10 ** (-10))) * t_a * D_t_r * D_t_r * D_t_r * D_t_r +
            (9.22652254 * (10 ** (-12))) * t_a * t_a * D_t_r * D_t_r * D_t_r * D_t_r +
            (-5.08220384 * (10 ** (-9))) * vel * D_t_r * D_t_r * D_t_r * D_t_r +
            (-2.24730961 * (10 ** (-11))) * t_a * vel * D_t_r * D_t_r * D_t_r * D_t_r +
            (1.17139133 * (10 ** (-10))) * vel * vel * D_t_r * D_t_r * D_t_r * D_t_r +
            (6.62154879 * (10 ** (-10))) * D_t_r * D_t_r * D_t_r * D_t_r * D_t_r +
            (4.03863260 * (10 ** (-13))) * t_a * D_t_r * D_t_r * D_t_r * D_t_r * D_t_r +
            (1.95087203 * (10 ** (-12))) * vel * D_t_r * D_t_r * D_t_r * D_t_r * D_t_r +
            (-4.73602469 * (10 ** (-12))) * D_t_r * D_t_r * D_t_r * D_t_r * D_t_r * D_t_r +
            (5.12733497) * v_p+
            (-0.312788561) * t_a * v_p+
            (-0.0196701861) * t_a * t_a * v_p+
            (9.99690870 * (10 ** (-4))) * t_a * t_a * t_a * v_p+
            (9.51738512 * (10 ** (-6))) * t_a * t_a * t_a * t_a * v_p+
            (-4.66426341 * (10 ** (-7))) * t_a * t_a * t_a * t_a * t_a * v_p+
            (0.548050612) * vel * v_p+
            (-0.00330552823) * t_a * vel * v_p+
            (-0.00164119440) * t_a * t_a * vel * v_p+
            (-5.16670694 * (10 ** (-6))) * t_a * t_a * t_a * vel * v_p+
            (9.52692432 * (10 ** (-7))) * t_a * t_a * t_a * t_a * vel * v_p+
            (-0.0429223622) * vel * vel * v_p+
            (0.00500845667) * t_a * vel * vel * v_p+
            (1.00601257 * (10 ** (-6))) * t_a * t_a * vel * vel * v_p+
            (-1.81748644 * (10 ** (-6))) * t_a * t_a * t_a * vel * vel * v_p+
            (-1.25813502 * (10 ** (-3))) * vel * vel * vel * v_p+
            (-1.79330391 * (10 ** (-4))) * t_a * vel * vel * vel * v_p+
            (2.34994441 * (10 ** (-6))) * t_a * t_a * vel * vel * vel * v_p+
            (1.29735808 * (10 ** (-4))) * vel * vel * vel * vel * v_p+
            (1.29064870 * (10 ** (-6))) * t_a * vel * vel * vel * vel * v_p+
            (-2.28558686 * (10 ** (-6))) * vel * vel * vel * vel * vel * v_p+
            (-0.0369476348) * D_t_r * v_p+
            (0.00162325322) * t_a * D_t_r * v_p+
            (-3.14279680 * (10 ** (-5))) * t_a * t_a * D_t_r * v_p+
            (2.59835559 * (10 ** (-6))) * t_a * t_a * t_a * D_t_r * v_p+
            (-4.77136523 * (10 ** (-8))) * t_a * t_a * t_a * t_a * D_t_r * v_p+
            (8.64203390 * (10 ** (-3))) * vel * D_t_r * v_p+
            (-6.87405181 * (10 ** (-4))) * t_a * vel * D_t_r * v_p+
            (-9.13863872 * (10 ** (-6))) * t_a * t_a * vel * D_t_r * v_p+
            (5.15916806 * (10 ** (-7))) * t_a * t_a * t_a * vel * D_t_r * v_p+
            (-3.59217476 * (10 ** (-5))) * vel * vel * D_t_r * v_p+
            (3.28696511 * (10 ** (-5))) * t_a * vel * vel * D_t_r * v_p+
            (-7.10542454 * (10 ** (-7))) * t_a * t_a * vel * vel * D_t_r * v_p+
            (-1.24382300 * (10 ** (-5))) * vel * vel * vel * D_t_r * v_p+
            (-7.38584400 * (10 ** (-9))) * t_a * vel * vel * vel * D_t_r * v_p+
            (2.20609296 * (10 ** (-7))) * vel * vel * vel * vel * D_t_r * v_p+
            (-7.32469180 * (10 ** (-4))) * D_t_r * D_t_r * v_p+
            (-1.87381964 * (10 ** (-5))) * t_a * D_t_r * D_t_r * v_p+
            (4.80925239 * (10 ** (-6))) * t_a * t_a * D_t_r * D_t_r * v_p+
            (-8.75492040 * (10 ** (-8))) * t_a * t_a * t_a * D_t_r * D_t_r * v_p+
            (2.77862930 * (10 ** (-5))) * vel * D_t_r * D_t_r * v_p+
            (-5.06004592 * (10 ** (-6))) * t_a * vel * D_t_r * D_t_r * v_p+
            (1.14325367 * (10 ** (-7))) * t_a * t_a * vel * D_t_r * D_t_r * v_p+
            (2.53016723 * (10 ** (-6))) * vel * vel * D_t_r * D_t_r * v_p+
            (-1.72857035 * (10 ** (-8))) * t_a * vel * vel * D_t_r * D_t_r * v_p+
            (-3.95079398 * (10 ** (-8))) * vel * vel * vel * D_t_r * D_t_r * v_p+
            (-3.59413173 * (10 ** (-7))) * D_t_r * D_t_r * D_t_r * v_p+
            (7.04388046 * (10 ** (-7))) * t_a * D_t_r * D_t_r * D_t_r * v_p+
            (-1.89309167 * (10 ** (-8))) * t_a * t_a * D_t_r * D_t_r * D_t_r * v_p+
            (-4.79768731 * (10 ** (-7))) * vel * D_t_r * D_t_r * D_t_r * v_p+
            (7.96079978 * (10 ** (-9))) * t_a * vel * D_t_r * D_t_r * D_t_r * v_p+
            (1.62897058 * (10 ** (-9))) * vel * vel * D_t_r * D_t_r * D_t_r * v_p+
            (3.94367674 * (10 ** (-8))) * D_t_r * D_t_r * D_t_r * D_t_r * v_p+
            (-1.18566247 * (10 ** (-9))) * t_a * D_t_r * D_t_r * D_t_r * D_t_r * v_p+
            (3.34678041 * (10 ** (-10))) * vel * D_t_r * D_t_r * D_t_r * D_t_r * v_p+
            (-1.15606447 * (10 ** (-10))) * D_t_r * D_t_r * D_t_r * D_t_r * D_t_r * v_p+
            (-2.80626406) * v_p* v_p+
            (0.548712484) * t_a * v_p* v_p+
            (-0.00399428410) * t_a * t_a * v_p* v_p+
            (-9.54009191 * (10 ** (-4))) * t_a * t_a * t_a * v_p* v_p+
            (1.93090978 * (10 ** (-5))) * t_a * t_a * t_a * t_a * v_p* v_p+
            (-0.308806365) * vel * v_p* v_p+
            (0.0116952364) * t_a * vel * v_p* v_p+
            (4.95271903 * (10 ** (-4))) * t_a * t_a * vel * v_p* v_p+
            (-1.90710882 * (10 ** (-5))) * t_a * t_a * t_a * vel * v_p* v_p+
            (0.00210787756) * vel * vel * v_p* v_p+
            (-6.98445738 * (10 ** (-4))) * t_a * vel * vel * v_p* v_p+
            (2.30109073 * (10 ** (-5))) * t_a * t_a * vel * vel * v_p* v_p+
            (4.17856590 * (10 ** (-4))) * vel * vel * vel * v_p* v_p+
            (-1.27043871 * (10 ** (-5))) * t_a * vel * vel * vel * v_p* v_p+
            (-3.04620472 * (10 ** (-6))) * vel * vel * vel * vel * v_p* v_p+
            (0.0514507424) * D_t_r * v_p* v_p+
            (-0.00432510997) * t_a * D_t_r * v_p* v_p+
            (8.99281156 * (10 ** (-5))) * t_a * t_a * D_t_r * v_p* v_p+
            (-7.14663943 * (10 ** (-7))) * t_a * t_a * t_a * D_t_r * v_p* v_p+
            (-2.66016305 * (10 ** (-4))) * vel * D_t_r * v_p* v_p+
            (2.63789586 * (10 ** (-4))) * t_a * vel * D_t_r * v_p* v_p+
            (-7.01199003 * (10 ** (-6))) * t_a * t_a * vel * D_t_r * v_p* v_p+
            (-1.06823306 * (10 ** (-4))) * vel * vel * D_t_r * v_p* v_p+
            (3.61341136 * (10 ** (-6))) * t_a * vel * vel * D_t_r * v_p* v_p+
            (2.29748967 * (10 ** (-7))) * vel * vel * vel * D_t_r * v_p* v_p+
            (3.04788893 * (10 ** (-4))) * D_t_r * D_t_r * v_p* v_p+
            (-6.42070836 * (10 ** (-5))) * t_a * D_t_r * D_t_r * v_p* v_p+
            (1.16257971 * (10 ** (-6))) * t_a * t_a * D_t_r * D_t_r * v_p* v_p+
            (7.68023384 * (10 ** (-6))) * vel * D_t_r * D_t_r * v_p* v_p+
            (-5.47446896 * (10 ** (-7))) * t_a * vel * D_t_r * D_t_r * v_p* v_p+
            (-3.59937910 * (10 ** (-8))) * vel * vel * D_t_r * D_t_r * v_p* v_p+
            (-4.36497725 * (10 ** (-6))) * D_t_r * D_t_r * D_t_r * v_p* v_p+
            (1.68737969 * (10 ** (-7))) * t_a * D_t_r * D_t_r * D_t_r * v_p* v_p+
            (2.67489271 * (10 ** (-8))) * vel * D_t_r * D_t_r * D_t_r * v_p* v_p+
            (3.23926897 * (10 ** (-9))) * D_t_r * D_t_r * D_t_r * D_t_r * v_p* v_p+
            (-0.0353874123) * v_p* v_p* v_p+
            (-0.221201190) * t_a * v_p* v_p* v_p+
            (0.0155126038) * t_a * t_a * v_p* v_p* v_p+
            (-2.63917279 * (10 ** (-4))) * t_a * t_a * t_a * v_p* v_p* v_p+
            (0.0453433455) * vel * v_p* v_p* v_p+
            (-0.00432943862) * t_a * vel * v_p* v_p* v_p+
            (1.45389826 * (10 ** (-4))) * t_a * t_a * vel * v_p* v_p* v_p+
            (2.17508610 * (10 ** (-4))) * vel * vel * v_p* v_p* v_p+
            (-6.66724702 * (10 ** (-5))) * t_a * vel * vel * v_p* v_p* v_p+
            (3.33217140 * (10 ** (-5))) * vel * vel * vel * v_p* v_p* v_p+
            (-0.00226921615) * D_t_r * v_p* v_p* v_p+
            (3.80261982 * (10 ** (-4))) * t_a * D_t_r * v_p* v_p* v_p+
            (-5.45314314 * (10 ** (-9))) * t_a * t_a * D_t_r * v_p* v_p* v_p+
            (-7.96355448 * (10 ** (-4))) * vel * D_t_r * v_p* v_p* v_p+
            (2.53458034 * (10 ** (-5))) * t_a * vel * D_t_r * v_p* v_p* v_p+
            (-6.31223658 * (10 ** (-6))) * vel * vel * D_t_r * v_p* v_p* v_p+
            (3.02122035 * (10 ** (-4))) * D_t_r * D_t_r * v_p* v_p* v_p+
            (-4.77403547 * (10 ** (-6))) * t_a * D_t_r * D_t_r * v_p* v_p* v_p+
            (1.73825715 * (10 ** (-6))) * vel * D_t_r * D_t_r * v_p* v_p* v_p+
            (-4.09087898 * (10 ** (-7))) * D_t_r * D_t_r * D_t_r * v_p* v_p* v_p+
            (0.614155345) * v_p* v_p* v_p* v_p+
            (-0.0616755931) * t_a * v_p* v_p* v_p* v_p+
            (0.00133374846) * t_a * t_a * v_p* v_p* v_p* v_p+
            (0.00355375387) * vel * v_p* v_p* v_p* v_p+
            (-5.13027851 * (10 ** (-4))) * t_a * vel * v_p* v_p* v_p* v_p+
            (1.02449757 * (10 ** (-4))) * vel * vel * v_p* v_p* v_p* v_p+
            (-0.00148526421) * D_t_r * v_p* v_p* v_p* v_p+
            (-4.11469183 * (10 ** (-5))) * t_a * D_t_r * v_p* v_p* v_p* v_p+ 
            (-6.80434415 * (10 ** (-6))) * vel * D_t_r * v_p* v_p* v_p* v_p+
            (-9.77675906 * (10 ** (-6))) * D_t_r * D_t_r * v_p* v_p* v_p* v_p+
            (0.0882773108) * v_p* v_p* v_p* v_p* v_p+
            (-0.00301859306) * t_a * v_p* v_p* v_p* v_p* v_p+
            (0.00104452989) * vel * v_p* v_p* v_p* v_p* v_p+
            (2.47090539 * (10 ** (-4))) * D_t_r * v_p* v_p* v_p* v_p* v_p+
            (0.00148348065) * v_p* v_p* v_p* v_p* v_p* v_p)

        comfortable = (UTCI_approx > 9 && UTCI_approx < 26) ? true : false
        stressRange = 2
        stressRange = (UTCI_approx < -14.0) ? -2 : stressRange
        stressRange = (UTCI_approx < 9.0) ? -1 : stressRange
        stressRange = (UTCI_approx < 26.0) ? 0 : stressRange
        stressRange = (UTCI_approx < 32.0) ? 1 : stressRange
    } else {
        UTCI_approx = "None"
        comfortable = "None"
        stressRange = "None"
    }
    log("UTCI approx is : ${UTCI_approx}","debug")
    return ['utci_approx': UTCI_approx, 'comfortable': comfortable, 'stress_range': stressRange]
}

def sun_info(t_a, t_r, vel, v_p){
}
