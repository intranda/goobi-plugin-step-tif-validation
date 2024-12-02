---
title: Tif-Validation
identifier: intranda_step_jhove-validation
published: true
description: This step plugin allows you to validate images using JHove and customize the workflow
---
## Introduction
This plugin is used to validate images in `TIF` format within defined directories. The validation is done with the help of the [open source software library JHove](https://jhove.openpreservation.org/) and is extensively configurable.


## Installation
To install the plugin, the following file must be installed first:

```bash
/opt/digiverso/goobi/plugins/step/plugin_intranda_step_tif_validation-base.jar
```

To configure how the plugin should behave, different values can be adjusted in the configuration file. The central configuration file is usually located here:

```bash
/opt/digiverso/goobi/config/plugin_intranda_step_tif_validation.xml
```

Within this configuration file the path to the JHove configuration is named. In the case of the example below, the following path is given:

```xml
<jhoveConfiguration>/opt/digiverso/goobi/config/jhove/jhove.conf</jhoveConfiguration>
```

Accordingly, the following two files must also be installed under this path:

```xml
/opt/digiverso/goobi/config/jhove/jhove.conf
/opt/digiverso/goobi/config/jhove/jhoveConfig.xsd
```


## Overview and functionality
The plugin is usually executed fully automatically within the workflow. It first determines whether there is a block within the configuration file that has been configured for the current workflow with respect to project name and work step. If this is the case, the other parameters are evaluated and the checks are started. If one of the configured checks is not successful, the configured or alternatively the previous work step is set to an error status and the validation message is written to the process log. If the work steps between the validation step and the notified step are to be set in the status to closed, these steps are also provided with the correction message for the agents and thus allow the problem case to be traced.

This plugin is integrated into the workflow in such a way that it is executed automatically. Manual interaction with the plugin is not necessary. For use within a step of the workflow it should be configured as shown in the following screenshot.

![Integration of the plugin into the workflow](screen1.png)


## Configuration
The configuration for the plugin is done within the central configuration file. It looks like the following example:

{{CONFIG_CONTENT}}

{{CONFIG_DESCRIPTION_PROJECT_STEP}}

Parameter         | Explanation
------------------|----------------------------------------
`folder` | This parameter can be used to specify directories whose contents are to be validated. This parameter can occur repeatedly. Possible values are `master`, `media` or individual folders like `photos` and `scans`.
`openStepOnError` | This parameter determines which step of the workflow should be reopened if an error occurs within the validation. If this parameter is not used, the plugin simply activates the previous step of the validation step instead.
`lockAllStepsBetween` | This parameter is used to determine whether the work steps of the workflow between the validation step and the one specified within the parameter openStepOnError are to be set back to the status locked so that these work steps have to be run through again (`true`). If, on the other hand, the value is set to `false`, the status of the steps in between is not changed, so that these steps are not run through again.
`jhoveConfiguration` | This parameter specifies where the configuration file for JHove is located. 
`check` | Within each element check is defined what exactly JHove should validate. For example, here you define which file format is expected. For the expected value, a direct input can also be entered as a range within the `<wanted>` element. It is also possible to use a variable here, which is replaced by the variable replacer (e.g. `{process.Resolution}`. Also included is which error message should be issued in case of an incorrect validation. The following variables can be used in the error message: `${wanted}` for the exact content from the `<wanted>` field, `${expected}` for the resolved expected value, `${found}` for the found value and `${image}` for the file name. The field `<wanted>` can be repeated and can contain the sub-element `<condition>`. The check is then only executed if the configured condition applies.

