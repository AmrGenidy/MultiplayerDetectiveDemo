package common.dto;

import java.io.Serializable;
import java.util.List;
import java.util.ArrayList;
import java.util.Objects;

public class ClientCaseListDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    private final List<CaseInfoDTO> cases;

    public ClientCaseListDTO(List<CaseInfoDTO> cases) {
        this.cases = cases != null ? new ArrayList<>(cases) : new ArrayList<>();
    }

    public List<CaseInfoDTO> getCases() {
        return new ArrayList<>(cases); // Return a copy
    }

    @Override
    public String toString() {
        return "ClientCaseListDTO{" + "cases_count=" + cases.size() + '}';
    }
}