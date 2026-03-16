package com.clmextract.api.mapper;

import com.clmextract.api.dto.MetadataNodeDto;
import com.clmextract.api.dto.PropertyDto;
import com.clmextract.metadata.BoMetadata;
import com.clmextract.metadata.ComponentMetadata;
import com.clmextract.metadata.FieldMetadata;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MetadataMapper {

    public BoMetadata map(List<MetadataNodeDto> nodes) {
        BoMetadata metadata = new BoMetadata();

        // Step 1: Find BundleProperties node for BO-level info
        for (MetadataNodeDto node : nodes) {
            if ("BundleProperties".equals(node.getListType())) {
                metadata.setBoName(findPropertyValue(node.getProperties(), "name"));
                metadata.setBoUsageType(findPropertyValue(node.getProperties(), "usageType"));
                break;
            }
        }

        // Step 2: Index ComponentProperties nodes by id
        Map<String, ComponentMetadata> componentById = new LinkedHashMap<>();
        for (MetadataNodeDto node : nodes) {
            if ("ComponentProperties".equals(node.getListType())) {
                ComponentMetadata comp = new ComponentMetadata();
                comp.setInternalName(findPropertyValue(node.getProperties(), "name"));
                comp.setDisplayName(findPropertyValue(node.getProperties(), "displayName"));
                comp.setCardinality(findPropertyValue(node.getProperties(), "cardinality"));

                // Use InstancePath from any property to determine the component's instancePath
                if (node.getProperties() != null && !node.getProperties().isEmpty()) {
                    comp.setInstancePath(node.getProperties().get(0).getInstancePath());
                }

                comp.setFields(new ArrayList<>());
                componentById.put(node.getId(), comp);
            }
        }

        // Step 3: Find ParameterProperties and assign to parent component
        for (MetadataNodeDto node : nodes) {
            if ("ParameterProperties".equals(node.getListType())) {
                ComponentMetadata parent = componentById.get(node.getParentId());
                if (parent == null) {
                    continue;
                }

                FieldMetadata field = new FieldMetadata();
                field.setInternalName(findPropertyValue(node.getProperties(), "name"));
                field.setDisplayName(findPropertyValue(node.getProperties(), "displayName"));
                field.setDataType(findPropertyValue(node.getProperties(), "dataType"));

                // Use InstancePath from property (should be MCPDef:/Module/Component/Field)
                if (node.getProperties() != null && !node.getProperties().isEmpty()) {
                    field.setInstancePath(node.getProperties().get(0).getInstancePath());
                }

                parent.getFields().add(field);
            }
        }

        // Inject serverFileName for attachment components — it is a system field not in ParameterProperties
        // but the API will return it when explicitly requested via fieldPaths
        for (ComponentMetadata comp : componentById.values()) {
            String name = comp.getInternalName();
            if (!"ReqAttachment".equals(name) && !"ReqContractAttachment".equals(name)) continue;

            List<FieldMetadata> fields = comp.getFields();
            boolean alreadyPresent = fields.stream().anyMatch(f -> "serverFileName".equals(f.getInternalName()));
            if (alreadyPresent) continue;

            // Derive instancePath from the first field that has one: replace the last segment with serverFileName
            String injectedPath = null;
            for (FieldMetadata f : fields) {
                if (f.getInstancePath() != null) {
                    int lastSlash = f.getInstancePath().lastIndexOf('/');
                    if (lastSlash >= 0) {
                        injectedPath = f.getInstancePath().substring(0, lastSlash + 1) + "serverFileName";
                    }
                    break;
                }
            }
            if (injectedPath == null) continue;

            FieldMetadata serverFileName = new FieldMetadata();
            serverFileName.setInternalName("serverFileName");
            serverFileName.setDisplayName("File Path");
            serverFileName.setInstancePath(injectedPath);
            fields.add(serverFileName);
        }

        metadata.setComponents(new ArrayList<>(componentById.values()));
        return metadata;
    }

    private String findPropertyValue(List<PropertyDto> properties, String name) {
        if (properties == null) {
            return null;
        }
        for (PropertyDto prop : properties) {
            if (name.equals(prop.getName())) {
                return prop.getValue();
            }
        }
        return null;
    }
}
