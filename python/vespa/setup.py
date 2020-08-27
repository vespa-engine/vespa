import os
import setuptools


def get_target_version():
    build_nr = os.environ.get("GITHUB_RUN_NUMBER", "0+dev")
    version = "0.1"
    return "{}.{}".format(version, build_nr)


min_python = "3.6"

setuptools.setup(
    name="pyvespa",
    version=get_target_version(),
    description="Python API for vespa.ai",
    keywords="vespa, search engine, data science",
    author="Thiago G. Martins",
    author_email="tmartins@verizonmedia.com",
    license=(
        "Apache Software License 2.0",
        "OSI Approved :: Apache Software License",
    ),
    packages=setuptools.find_packages(),
    include_package_data=True,
    install_requires=["requests", "pandas", "docker", "jinja2"],
    python_requires=">=3.6",
    zip_safe=False,
    data_files=[
        (
            "templates",
            [
                "vespa/templates/hosts.xml",
                "vespa/templates/services.xml",
                "vespa/templates/schema.txt",
            ],
        )
    ],
)
