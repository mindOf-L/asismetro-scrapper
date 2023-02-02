package xyz.asismetro.scrapper;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder(toBuilder = true)
public class Brother {

    private String name;
    private String email;
    private String phone;
    private String ppam;
    private Map<String, String> shifts;
}
