package com.bloomfield.terminal.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.bloomfield.terminal.user.api.Role;
import com.bloomfield.terminal.user.api.UserDirectory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class DemoUserSeederIT extends AbstractDemoProfileIT {

  @Autowired UserDirectory userDirectory;

  @Test
  void seedsThreeDemoUsersWithExpectedRoles() {
    var admin = userDirectory.findByEmail("admin@altaryslabs.com").orElseThrow();
    var analyst = userDirectory.findByEmail("analyst@demo.bloomfield").orElseThrow();
    var viewer = userDirectory.findByEmail("viewer@demo.bloomfield").orElseThrow();

    assertThat(admin.roles()).containsExactly(Role.ADMIN);
    assertThat(admin.enabled()).isTrue();
    assertThat(analyst.roles()).containsExactly(Role.ANALYST);
    assertThat(viewer.roles()).containsExactly(Role.VIEWER);
  }
}
