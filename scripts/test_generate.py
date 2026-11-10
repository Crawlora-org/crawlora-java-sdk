"""Regression tests for Java SDK generation edge cases."""
import unittest

import generate
from _sdkgen import core


class RenderOperationsTests(unittest.TestCase):
    def test_group_metadata_is_split_into_jvm_safe_helpers(self):
        groups = {
            f"platform-{index}": {"lookup": f"platform-{index}-lookup"}
            for index in range(61)
        }
        model = core.Model(
            groups=groups,
            operations={},
            meta={},
            definitions={},
            operation_count=61,
        )

        source = generate.render_operations(model)

        self.assertIn("putGroups0(g);", source)
        self.assertIn("putGroups1(g);", source)
        self.assertIn("putGroups2(g);", source)
        self.assertIn('g.put("platform-60", Map.copyOf(group));', source)


if __name__ == "__main__":
    unittest.main()
