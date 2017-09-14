package com.hpe.srf.automation.tools.model;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

class Pair extends AbstractDescribableImpl<Pair> {

    @DataBoundConstructor
    public Pair(String name, String value) {
        this.name = name;
        this.value = value;
    }

    public String name;
    public  String value;
    @Extension
    public static class DescriptorImpl extends Descriptor<Pair> {

        public String getDisplayName() {
            return "Test Parameter";
        }


        public FormValidation doCheckName(@QueryParameter String value) {

            FormValidation ret = FormValidation.ok();
            if (StringUtils.isBlank(value)) {
                ret = FormValidation.error("Parameter name must be set");
            }

            return ret;
        }
    }
}